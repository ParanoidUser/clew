package dev.noid.clew.cli;

import dev.noid.clew.stack.ClewLog;
import dev.noid.clew.stack.ClewStack;
import java.io.PrintStream;
import java.util.List;
import java.util.NoSuchElementException;

public class CommandHandler {

  private final String[] args;
  private final ClewStack stack;
  private final ClewLog log;
  private final ReviseHandler revise;
  private final PrintStream stdout;
  private final PrintStream stderr;

  public CommandHandler(
      String[] args,
      ClewStack stack,
      ClewLog log,
      ReviseHandler revise,
      PrintStream stdout,
      PrintStream stderr) {
    this.args = args;
    this.stack = stack;
    this.log = log;
    this.revise = revise;
    this.stdout = stdout;
    this.stderr = stderr;
  }

  public int invoke() {
    if (args.length == 0) {
      stderr.println("no command provided");
      return 1;
    }
    String cmd = args[0];
    if (!cmd.equals("revise") && revise.isInProgress()) {
      stderr.println("error: revise in progress. Run 'clew revise' to continue or cancel.");
      return 1;
    }
    return switch (cmd) {
      case "ls" -> invokeList();
      case "log" -> invokeLog();
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
    int top = items.size() - 1;
    for (int i = top; i >= 0; i--) {
      if (i == top) {
        stdout.printf("[%d] \u25cb %s%n", i + 1, items.get(i));
      } else {
        stdout.printf("[%d] \u001b[2m\u25cb %s\u001b[0m%n", i + 1, items.get(i));
      }
    }
    return 0;
  }

  private int invokeLog() {
    List<String> items = log.list();
    if (items.isEmpty()) {
      stdout.println("no completed tasks");
      return 0;
    }
    for (int i = items.size() - 1; i >= 0; i--) {
      stdout.printf("[%d] \u001b[2m\u2713 %s\u001b[0m%n", i + 1, items.get(i));
    }
    return 0;
  }

  private int invokePeek() {
    try {
      stdout.println(stack.peek());
      return 0;
    } catch (NoSuchElementException cause) {
      stderr.println("stack is empty");
      return 1;
    }
  }

  private int invokePop() {
    try {
      stdout.println(stack.pop());
      return 0;
    } catch (NoSuchElementException cause) {
      stderr.println("stack is empty");
      return 1;
    }
  }

  private int invokePush(String message) {
    if (message == null) {
      stderr.println("push requires a message");
      return 1;
    }
    stack.push(message);
    return 0;
  }

  private int invokeRevise() {
    try {
      if (!revise.isInProgress() && stack.list().isEmpty()) {
        stderr.println("stack is empty, nothing to revise");
        return 1;
      }
      revise.run();
      return 0;
    } catch (Exception cause) {
      stderr.println("error: " + cause.getMessage());
      return 1;
    }
  }
}
