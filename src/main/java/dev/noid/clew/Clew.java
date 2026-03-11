package dev.noid.clew;

import dev.noid.clew.cli.CommandHandler;
import dev.noid.clew.cli.ReviseHandler;
import dev.noid.clew.cli.ReviseView;
import dev.noid.clew.codec.JacksonJournalCodec;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.file.FileJournal;
import dev.noid.clew.stack.ClewLog;
import dev.noid.clew.stack.ClewStack;
import dev.noid.clew.stack.ReviseState;
import java.nio.file.Files;
import java.nio.file.Path;

public class Clew {

  static void main(String[] args) {
    Path clewDir = Path.of(System.getProperty("user.home"), ".clew");
    FileJournal journal = new FileJournal(clewDir.resolve("wal"), 10 * 1024 * 1024);

    JournalCodec<JournalRecord> codec = new JacksonJournalCodec<>(JournalRecord.class);
    ClewStack stack = new ClewStack(journal, codec);
    ClewLog log = new ClewLog(journal, codec);

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

    CommandHandler handler = new CommandHandler(args, stack, log, revise, System.out, System.err);
    System.exit(handler.invoke());
  }
}
