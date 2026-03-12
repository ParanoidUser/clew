package dev.noid.clew.projection;

import dev.noid.clew.TaskEvent;
import dev.noid.clew.TaskEvent.*;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.Journal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projection of active (non-completed, non-dropped) tasks. Replays the journal to build the
 * current backlog with ordering and active task identification.
 */
public class Backlog {

  private final Journal journal;
  private final JournalCodec<TaskEvent> codec;

  public Backlog(Journal journal, JournalCodec<TaskEvent> codec) {
    this.journal = journal;
    this.codec = codec;
  }

  public List<Task> tasks() {
    Map<String, Task> tasks = new LinkedHashMap<>();
    String activeTaskId = null;

    try (var stream = journal.openStream(0)) {
      for (TaskEvent event : stream.map(codec::decode).toList()) {
        switch (event) {
          case TaskCreated e -> tasks.put(e.taskId(), new Task(e.taskId(), e.description(), false));
          case TaskCompleted e -> tasks.remove(e.taskId());
          case TaskDropped e -> tasks.remove(e.taskId());
          case TaskActivated e -> activeTaskId = e.taskId();
          case TaskDeactivated e -> {
            if (e.taskId().equals(activeTaskId)) {
              activeTaskId = null;
            }
          }
          case TaskContentUpdated e -> {
            Task existing = tasks.get(e.taskId());
            if (existing != null) {
              tasks.put(e.taskId(), new Task(e.taskId(), e.description(), existing.active()));
            }
          }
        }
      }
    }

    List<Task> result = new ArrayList<>(tasks.values());
    for (int i = 0; i < result.size(); i++) {
      Task t = result.get(i);
      boolean isActive = t.taskId().equals(activeTaskId);
      if (t.active() != isActive) {
        result.set(i, new Task(t.taskId(), t.description(), isActive));
      }
    }
    return List.copyOf(result);
  }

  public record Task(String taskId, String description, boolean active) {}
}
