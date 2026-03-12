package dev.noid.clew;

public sealed interface TaskEvent permits
    TaskEvent.TaskCreated,
    TaskEvent.TaskCompleted,
    TaskEvent.TaskDropped,
    TaskEvent.TaskActivated,
    TaskEvent.TaskDeactivated,
    TaskEvent.TaskContentUpdated {

  record TaskCreated(long timestamp, String taskId, String description) implements TaskEvent {}

  record TaskCompleted(long timestamp, String taskId) implements TaskEvent {}

  record TaskDropped(long timestamp, String taskId) implements TaskEvent {}

  record TaskActivated(long timestamp, String taskId) implements TaskEvent {}

  record TaskDeactivated(long timestamp, String taskId) implements TaskEvent {}

  record TaskContentUpdated(long timestamp, String taskId, String description) implements TaskEvent {}
}
