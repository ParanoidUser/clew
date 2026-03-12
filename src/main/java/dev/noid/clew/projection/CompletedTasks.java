package dev.noid.clew.projection;

import dev.noid.clew.TaskEvent;
import dev.noid.clew.TaskEvent.*;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.Journal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Projection of completed tasks. Replays the journal to build the log of finished work. Only tasks
 * that received a {@link TaskCompleted} event appear here. Dropped tasks are excluded.
 */
public class CompletedTasks {

  private final Journal journal;
  private final JournalCodec<TaskEvent> codec;

  public CompletedTasks(Journal journal, JournalCodec<TaskEvent> codec) {
    this.journal = journal;
    this.codec = codec;
  }

  public List<Task> tasks() {
    Map<String, String> descriptions = new LinkedHashMap<>();
    List<Task> completed = new ArrayList<>();

    try (var stream = journal.openStream(0)) {
      for (TaskEvent event : stream.map(codec::decode).toList()) {
        switch (event) {
          case TaskCreated e -> descriptions.put(e.taskId(), e.description());
          case TaskContentUpdated e -> descriptions.put(e.taskId(), e.description());
          case TaskCompleted e -> {
            String desc = descriptions.remove(e.taskId());
            if (desc != null) {
              completed.add(new Task(e.taskId(), desc));
            }
          }
          case TaskDropped e -> descriptions.remove(e.taskId());
          case TaskActivated ignored -> {}
          case TaskDeactivated ignored -> {}
        }
      }
    }

    return List.copyOf(completed);
  }

  public record Task(String taskId, String description) {}
}
