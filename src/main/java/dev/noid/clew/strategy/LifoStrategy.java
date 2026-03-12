package dev.noid.clew.strategy;

import dev.noid.clew.TaskEvent;
import dev.noid.clew.TaskEvent.*;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.Journal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * LIFO (stack-based) task management strategy. Accepts user commands, validates them against
 * current state, and emits domain events to the journal.
 * <p>
 * In LIFO, the most recently added task is always the active (current) task. Completing or
 * dropping the active task makes the next one active.
 */
public class LifoStrategy {

  private final Journal journal;
  private final JournalCodec<TaskEvent> codec;

  public LifoStrategy(Journal journal, JournalCodec<TaskEvent> codec) {
    this.journal = journal;
    this.codec = codec;
  }

  public String addTask(String description) {
    Deque<TaskEntry> stack = replayStack();
    long ts = System.currentTimeMillis();
    String taskId = UUID.randomUUID().toString();

    List<byte[]> batch = new java.util.ArrayList<>();
    if (!stack.isEmpty()) {
      batch.add(codec.encode(new TaskDeactivated(ts, stack.peekLast().taskId)));
    }
    batch.add(codec.encode(new TaskCreated(ts, taskId, description)));
    batch.add(codec.encode(new TaskActivated(ts, taskId)));

    journal.append(batch);
    return taskId;
  }

  public String completeTask() {
    Deque<TaskEntry> stack = replayStack();
    if (stack.isEmpty()) {
      throw new NoSuchElementException("no active task");
    }

    long ts = System.currentTimeMillis();
    TaskEntry top = stack.removeLast();

    List<byte[]> batch = new java.util.ArrayList<>();
    batch.add(codec.encode(new TaskDeactivated(ts, top.taskId)));
    batch.add(codec.encode(new TaskCompleted(ts, top.taskId)));
    if (!stack.isEmpty()) {
      batch.add(codec.encode(new TaskActivated(ts, stack.peekLast().taskId)));
    }

    journal.append(batch);
    return top.description;
  }

  public String activeTask() {
    Deque<TaskEntry> stack = replayStack();
    if (stack.isEmpty()) {
      throw new NoSuchElementException("no active task");
    }
    return stack.peekLast().description;
  }

  private Deque<TaskEntry> replayStack() {
    Deque<TaskEntry> stack = new ArrayDeque<>();

    try (var stream = journal.openStream(0)) {
      for (TaskEvent event : stream.map(codec::decode).toList()) {
        switch (event) {
          case TaskCreated e -> stack.addLast(new TaskEntry(e.taskId(), e.description()));
          case TaskCompleted e -> removeById(stack, e.taskId());
          case TaskDropped e -> removeById(stack, e.taskId());
          case TaskContentUpdated e -> {
            for (int i = 0; i < stack.size(); i++) {
              // ArrayDeque doesn't support indexed access, rebuild if needed
            }
            // Simple approach: rebuild
            var it = stack.iterator();
            Deque<TaskEntry> updated = new ArrayDeque<>();
            while (it.hasNext()) {
              TaskEntry entry = it.next();
              if (entry.taskId.equals(e.taskId())) {
                updated.addLast(new TaskEntry(e.taskId(), e.description()));
              } else {
                updated.addLast(entry);
              }
            }
            stack = updated;
          }
          case TaskActivated ignored -> {}
          case TaskDeactivated ignored -> {}
        }
      }
    }

    return stack;
  }

  private static void removeById(Deque<TaskEntry> stack, String taskId) {
    stack.removeIf(e -> e.taskId.equals(taskId));
  }

  private record TaskEntry(String taskId, String description) {}
}
