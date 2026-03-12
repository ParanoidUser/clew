package dev.noid.clew.cli;

import dev.noid.clew.UserIntent;
import dev.noid.clew.UserIntent.Add;
import dev.noid.clew.UserIntent.Done;
import dev.noid.clew.UserIntent.ListTasks;
import dev.noid.clew.UserIntent.Plan;
import dev.noid.clew.UserIntent.ShowHelp;
import dev.noid.clew.UserIntent.ViewDetail;
import dev.noid.clew.UserIntent.ViewHistory;
import java.util.Arrays;
import java.util.List;

public class CliRegistry {

  private static final List<CommandSpec> SPECS = List.of(
      new CommandSpec("add", "a", "<subject>", "Capture a new task",
          args -> new Add(String.join(" ", args))),

      new CommandSpec("done", "d", "", "Complete the current task",
          args -> new Done()),

      new CommandSpec("list", "l", "", "Show active tasks",
          args -> new ListTasks()),

      new CommandSpec("view", "v", "", "See details/notes",
          args -> new ViewDetail()),

      new CommandSpec("log", "h", "", "Show history of completed work",
          args -> new ViewHistory()),

      new CommandSpec("plan", "p", "", "Interactive session to organize",
          args -> new Plan()),

      new CommandSpec("help", "?", "", "Show this guide",
          args -> new ShowHelp())
  );

  public static UserIntent parse(String[] args) {
    if (args.length == 0) {
      return new UserIntent.ViewDetail();
    }
    String input = args[0].toLowerCase();
    String[] remaining = Arrays.copyOfRange(args, 1, args.length);

    return SPECS.stream()
        .filter(cmd -> cmd.label().equals(input) || cmd.alias().equals(input))
        .findFirst()
        .map(cmd -> cmd.mapper().apply(remaining))
        .orElse(new ShowHelp());
  }

  public static void printHelp() {
    System.out.println("Usage: clew <command> [args]\n");
    for (var cmd : SPECS) {
      // Standard CLI format: command <required> [optional]
      System.out.printf("  %-4s %-12s %-20s %s%n", cmd.alias(), cmd.label(), cmd.usage(), cmd.description());
    }
  }
}