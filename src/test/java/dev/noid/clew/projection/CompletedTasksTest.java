package dev.noid.clew.projection;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.TaskEvent;
import dev.noid.clew.TaskEvent.*;
import dev.noid.clew.codec.JacksonJournalCodec;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.file.FileJournal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompletedTasksTest {

  private static final JournalCodec<TaskEvent> CODEC = new JacksonJournalCodec<>(TaskEvent.class);

  @TempDir
  Path temp;

  private FileJournal journal;
  private CompletedTasks completedTasks;

  @BeforeEach
  void setUp() {
    journal = new FileJournal(temp.resolve("wal.log"), 4096);
    completedTasks = new CompletedTasks(journal, CODEC);
  }

  @AfterEach
  void tearDown() {
    journal.close();
  }

  @Test
  @DisplayName("empty journal → no completed tasks")
  void emptyJournal() {
    assertTrue(completedTasks.tasks().isEmpty());
  }

  @Test
  @DisplayName("created task without completion → no completed tasks")
  void createdOnly() {
    String id = id();
    long ts = ts();
    journal.append(List.of(
        CODEC.encode(new TaskCreated(ts, id, "A")),
        CODEC.encode(new TaskActivated(ts, id))
    ));
    assertTrue(completedTasks.tasks().isEmpty());
  }

  @Test
  @DisplayName("created then completed → task appears in log")
  void createdThenCompleted() {
    String id = id();
    long ts = ts();
    journal.append(List.of(
        CODEC.encode(new TaskCreated(ts, id, "A")),
        CODEC.encode(new TaskActivated(ts, id)),
        CODEC.encode(new TaskDeactivated(ts, id)),
        CODEC.encode(new TaskCompleted(ts, id))
    ));
    List<String> descriptions = completedTasks.tasks().stream()
        .map(CompletedTasks.Task::description).toList();
    assertEquals(List.of("A"), descriptions);
  }

  @Test
  @DisplayName("created then dropped → task does NOT appear in log")
  void createdThenDropped() {
    String id = id();
    long ts = ts();
    journal.append(List.of(
        CODEC.encode(new TaskCreated(ts, id, "A")),
        CODEC.encode(new TaskActivated(ts, id)),
        CODEC.encode(new TaskDeactivated(ts, id)),
        CODEC.encode(new TaskDropped(ts, id))
    ));
    assertTrue(completedTasks.tasks().isEmpty());
  }

  @Test
  @DisplayName("multiple completions preserve order (oldest first)")
  void multipleCompletions() {
    String idA = id();
    String idB = id();
    long ts = ts();
    journal.append(List.of(
        CODEC.encode(new TaskCreated(ts, idA, "A")),
        CODEC.encode(new TaskActivated(ts, idA)),
        CODEC.encode(new TaskDeactivated(ts, idA)),
        CODEC.encode(new TaskCreated(ts, idB, "B")),
        CODEC.encode(new TaskActivated(ts, idB)),
        CODEC.encode(new TaskDeactivated(ts, idB)),
        CODEC.encode(new TaskCompleted(ts, idB)),
        CODEC.encode(new TaskActivated(ts, idA)),
        CODEC.encode(new TaskDeactivated(ts, idA)),
        CODEC.encode(new TaskCompleted(ts, idA))
    ));
    List<String> descriptions = completedTasks.tasks().stream()
        .map(CompletedTasks.Task::description).toList();
    assertEquals(List.of("B", "A"), descriptions);
  }

  @Test
  @DisplayName("mixed completed and dropped → only completed tasks in log")
  void mixedCompletedAndDropped() {
    String idA = id();
    String idB = id();
    String idC = id();
    long ts = ts();
    journal.append(List.of(
        CODEC.encode(new TaskCreated(ts, idA, "A")),
        CODEC.encode(new TaskActivated(ts, idA)),
        CODEC.encode(new TaskDeactivated(ts, idA)),
        CODEC.encode(new TaskCreated(ts, idB, "B")),
        CODEC.encode(new TaskActivated(ts, idB)),
        CODEC.encode(new TaskDeactivated(ts, idB)),
        CODEC.encode(new TaskCreated(ts, idC, "C")),
        CODEC.encode(new TaskActivated(ts, idC)),
        CODEC.encode(new TaskDeactivated(ts, idC)),
        CODEC.encode(new TaskCompleted(ts, idC)),
        CODEC.encode(new TaskDropped(ts, idB)),
        CODEC.encode(new TaskActivated(ts, idA)),
        CODEC.encode(new TaskDeactivated(ts, idA)),
        CODEC.encode(new TaskCompleted(ts, idA))
    ));
    List<String> descriptions = completedTasks.tasks().stream()
        .map(CompletedTasks.Task::description).toList();
    assertEquals(List.of("C", "A"), descriptions);
  }

  @Test
  @DisplayName("same description completed twice → appears twice")
  void sameDescriptionCompletedTwice() {
    String id1 = id();
    String id2 = id();
    long ts = ts();
    journal.append(List.of(
        CODEC.encode(new TaskCreated(ts, id1, "A")),
        CODEC.encode(new TaskActivated(ts, id1)),
        CODEC.encode(new TaskDeactivated(ts, id1)),
        CODEC.encode(new TaskCompleted(ts, id1)),
        CODEC.encode(new TaskCreated(ts, id2, "A")),
        CODEC.encode(new TaskActivated(ts, id2)),
        CODEC.encode(new TaskDeactivated(ts, id2)),
        CODEC.encode(new TaskCompleted(ts, id2))
    ));
    List<String> descriptions = completedTasks.tasks().stream()
        .map(CompletedTasks.Task::description).toList();
    assertEquals(List.of("A", "A"), descriptions);
  }

  @Test
  @DisplayName("active tasks not in completed log")
  void activeItemsExcluded() {
    String idA = id();
    String idB = id();
    long ts = ts();
    journal.append(List.of(
        CODEC.encode(new TaskCreated(ts, idA, "A")),
        CODEC.encode(new TaskActivated(ts, idA)),
        CODEC.encode(new TaskDeactivated(ts, idA)),
        CODEC.encode(new TaskCreated(ts, idB, "B")),
        CODEC.encode(new TaskActivated(ts, idB)),
        CODEC.encode(new TaskDeactivated(ts, idB)),
        CODEC.encode(new TaskCompleted(ts, idB))
    ));
    List<String> descriptions = completedTasks.tasks().stream()
        .map(CompletedTasks.Task::description).toList();
    assertEquals(List.of("B"), descriptions);
  }

  private static String id() {
    return UUID.randomUUID().toString();
  }

  private static long ts() {
    return System.currentTimeMillis();
  }
}
