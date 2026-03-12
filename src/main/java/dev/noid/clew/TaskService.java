package dev.noid.clew;

import dev.noid.clew.cli.ReviseHandler;
import dev.noid.clew.projection.Backlog;
import dev.noid.clew.strategy.LifoStrategy;
import java.util.NoSuchElementException;

public class TaskService {

  private final Backlog backlog;
  private final LifoStrategy strategy;
  private final ReviseHandler revise;

  public TaskService(Backlog backlog, LifoStrategy strategy, ReviseHandler revise) {
    this.backlog = backlog;
    this.strategy = strategy;
    this.revise = revise;
  }

  public void handleAdd(String message) {
    checkLock();
    if (message == null) {
      throw new IllegalArgumentException("Add requires a message");
    }
    strategy.addTask(message);
    System.out.printf("\u001b[1m→ %s\u001b[0m%n", message);
  }

  public void handleDone() {
    checkLock();
    try {
      String completed = strategy.completeTask();
      System.out.printf("\u001b[32m✓ %s\u001b[0m%n", completed);
    } catch (NoSuchElementException cause) {
      throw new IllegalStateException("Task list is empty");
    }
    try {
      System.out.printf("\u001b[1m→ %s\u001b[0m%n", strategy.activeTask());
    } catch (NoSuchElementException cause) {
      // ignore if next task is empty
    }
  }

  public void handlePlan() {
    if (!revise.isInProgress() && backlog.tasks().isEmpty()) {
      throw new IllegalStateException("Task list is empty");
    }
    revise.run();
  }

  private void checkLock() {
    if (revise.isInProgress()) {
      throw new IllegalStateException("Task plan is in progress. Save or cancel to continue.");
    }
  }
}