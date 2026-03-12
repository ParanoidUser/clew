package dev.noid.clew;

import dev.noid.clew.projection.Backlog;
import dev.noid.clew.projection.CompletedTasks;
import java.util.List;

public class ConsolePresenter {

  public void renderTask(String subject) {
    System.out.printf("\u001b[1m→ %s\u001b[0m%n", subject);
  }

  public void renderList(List<Backlog.Task> tasks) {
    for (int i = tasks.size() - 1; i >= 0; i--) {
      var task = tasks.get(i);
      String icon = task.active() ? "\u001b[1m→" : "○";
      System.out.printf("%s %s\u001b[0m%n", icon, task.description());
    }
  }

  public void renderLog(List<CompletedTasks.Task> tasks) {
    for (int i = tasks.size() - 1; i >= 0; i--) {
      System.out.printf("\u001b[32m✓ %s\u001b[0m%n", tasks.get(i).description());
    }
  }
}