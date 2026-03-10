package dev.noid.clew;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

public class CommandHandler {

  private final String[] args;
  private final ClewStack stack;
  private final DiskJournal wal;
  private final Path scratchFile;
  private final ReviseUi reviseUi;
  private final PrintStream stdout;
  private final PrintStream stderr;

  public CommandHandler(
      String[] args,
      ClewStack stack,
      DiskJournal wal,
      Path scratchFile,
      ReviseUi reviseUi,
      PrintStream stdout,
      PrintStream stderr) {
    this.args = args;
    this.stack = stack;
    this.wal = wal;
    this.scratchFile = scratchFile;
    this.reviseUi = reviseUi;
    this.stdout = stdout;
    this.stderr = stderr;
  }

  public int invoke() {
    if (args.length == 0) {
      stderr.println("no command provided");
      return 1;
    }
    String cmd = args[0];
    if (!cmd.equals("revise") && Files.exists(scratchFile)) {
      stderr.println("error: revise in progress. Run 'clew revise' to continue or cancel.");
      return 1;
    }
    return switch (cmd) {
      case "ls" -> invokeList();
      case "peek" -> invokePeek();
      case "pop" -> invokePop();
      case "push" -> invokePush(args.length > 1 ? args[1] : null);
      case "revise" -> invokeRevise();
      default -> {
        stderr.printf("unknown command '%s'%n", cmd);
        yield 1;
      }
    };
  }

  private int invokeList() {
    List<String> items = stack.list();
    for (int i = items.size() - 1; i >= 0; i--) {
      stdout.printf("[%d] %s%n", i + 1, items.get(i));
    }
    return 0;
  }

  private int invokePeek() {
    try {
      stdout.println(stack.peek());
      return 0;
    } catch (Exception cause) {
      stderr.println("stack is empty");
      return 1;
    }
  }

  private int invokePop() {
    try {
      stdout.println(stack.pop());
      return 0;
    } catch (Exception cause) {
      stderr.println("stack is empty");
      return 1;
    }
  }

  private int invokePush(String message) {
    if (message == null) {
      stderr.println("push requires a message");
      return 1;
    }
    try {
      stack.push(message);
      return 0;
    } catch (Exception cause){
      return 1;
    }
  }

  private int invokeRevise() {
    try {
      if (!Files.exists(scratchFile) && stack.list().isEmpty()) {
        stderr.println("stack is empty, nothing to revise");
        return 1;
      }
      ReviseState state = Files.exists(scratchFile)
          ? ReviseState.restore(wal, scratchFile)
          : ReviseState.start(wal, scratchFile);
      reviseUi.run(state);
      return 0;
    } catch (IllegalStateException cause) {
      stderr.println("error: " + cause.getMessage());
      return 1;
    } catch (IOException cause) {
      stderr.println("error: " + cause.getMessage());
      return 1;
    }
  }
}
