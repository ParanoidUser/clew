package dev.noid.clew;

import dev.noid.clew.cli.CliRegistry;
import dev.noid.clew.cli.ReviseHandler;
import dev.noid.clew.cli.ReviseView;
import dev.noid.clew.codec.JacksonJournalCodec;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.file.FileJournal;
import dev.noid.clew.projection.Backlog;
import dev.noid.clew.projection.CompletedTasks;
import dev.noid.clew.stack.ReviseState;
import dev.noid.clew.strategy.LifoStrategy;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

  static void main(String[] args) {
    Path clewDir = Path.of(System.getProperty("user.home"), ".clew");
    FileJournal journal = new FileJournal(clewDir.resolve("wal"), 10 * 1024 * 1024);

    JournalCodec<TaskEvent> codec = new JacksonJournalCodec<>(TaskEvent.class);
    LifoStrategy strategy = new LifoStrategy(journal, codec);
    Backlog backlog = new Backlog(journal, codec);
    CompletedTasks completedTasks = new CompletedTasks(journal, codec);

    Path scratchFile = clewDir.resolve("revise.tmp");
    ReviseView reviseView = new ReviseView();

    ReviseHandler revise = new ReviseHandler() {
      @Override
      public boolean isInProgress() {
        return Files.exists(scratchFile);
      }

      @Override
      public void run() {
        try {
          ReviseState state = Files.exists(scratchFile)
              ? ReviseState.restore(journal, codec, scratchFile)
              : ReviseState.start(journal, codec, scratchFile);
          reviseView.run(state);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };

    TaskService service = new TaskService(backlog, strategy, revise);
    ConsolePresenter presenter = new ConsolePresenter();

    UserIntent intent = CliRegistry.parse(args);
    try {
      switch (intent) {
        case UserIntent.Add(String subject) -> service.handleAdd(subject);
        case UserIntent.Done() -> service.handleDone();
        case UserIntent.Plan() -> service.handlePlan();
        case UserIntent.ViewDetail() -> presenter.renderTask(strategy.activeTask());
        case UserIntent.ListTasks() -> presenter.renderList(backlog.tasks());
        case UserIntent.ViewHistory() -> presenter.renderLog(completedTasks.tasks());
        default -> CliRegistry.printHelp();
      }
    } catch (Exception cause) {
      System.err.println("error: " + cause.getMessage());
      System.exit(1);
    }
  }
}
