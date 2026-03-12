package dev.noid.clew;

/**
 * Represents a discrete user command within the task management domain.
 */
public sealed interface UserIntent {

  /**
   * Captures a new task into the system's backlog.
   *
   * @param subject The core description of the work to be done.
   */
  record Add(String subject) implements UserIntent {}

  /**
   * Signals the completion of the system's current priority task.
   */
  record Done() implements UserIntent {}

  /**
   * Requests a detailed inspection of the current task.
   */
  record ViewDetail() implements UserIntent {}

  /**
   * Requests an overview of all tasks currently awaiting execution.
   */
  record ListTasks() implements UserIntent {}

  /**
   * Requests an audit log of tasks that have been completed.
   */
  record ViewHistory() implements UserIntent {}

  /**
   * Initiates a stateful session to reorganize the active work plan.
   */
  record Plan() implements UserIntent {}

  record ShowHelp() implements UserIntent {}
}