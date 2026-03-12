package dev.noid.clew.strategy;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.TaskEvent;
import dev.noid.clew.codec.JacksonJournalCodec;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.file.FileJournal;
import dev.noid.clew.projection.Backlog;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LifoStrategyTest {

  private static final JournalCodec<TaskEvent> CODEC = new JacksonJournalCodec<>(TaskEvent.class);

  @TempDir
  Path temp;

  private FileJournal journal;
  private LifoStrategy strategy;

  @BeforeEach
  void setUp() {
    journal = new FileJournal(temp.resolve("wal.log"), 4096);
    strategy = new LifoStrategy(journal, CODEC);
  }

  @AfterEach
  void tearDown() {
    journal.close();
  }

  @Test
  @DisplayName("new stack is empty")
  void emptyStack() {
    Backlog backlog = new Backlog(journal, CODEC);
    assertTrue(backlog.tasks().isEmpty());
  }

  @Test
  @DisplayName("addTask then activeTask returns pushed value")
  void addAndActive() {
    strategy.addTask("A");
    assertEquals("A", strategy.activeTask());
  }

  @Test
  @DisplayName("activeTask returns most recently added task")
  void activeReturnsMostRecent() {
    strategy.addTask("A");
    strategy.addTask("B");
    assertEquals("B", strategy.activeTask());
  }

  @Test
  @DisplayName("completeTask returns most recently added task")
  void completeReturnsMostRecent() {
    strategy.addTask("A");
    strategy.addTask("B");
    assertEquals("B", strategy.completeTask());
  }

  @Test
  @DisplayName("completeTask removes top, activeTask sees next")
  void completeThenActive() {
    strategy.addTask("A");
    strategy.addTask("B");
    strategy.completeTask();
    assertEquals("A", strategy.activeTask());
  }

  @Test
  @DisplayName("addTask then completeTask leaves backlog empty")
  void addCompleteEmpty() {
    strategy.addTask("A");
    strategy.completeTask();
    Backlog backlog = new Backlog(journal, CODEC);
    assertTrue(backlog.tasks().isEmpty());
  }

  @Test
  @DisplayName("backlog returns bottom-to-top order")
  void backlogOrder() {
    strategy.addTask("A");
    strategy.addTask("B");
    Backlog backlog = new Backlog(journal, CODEC);
    List<String> descriptions = backlog.tasks().stream().map(Backlog.Task::description).toList();
    assertEquals(List.of("A", "B"), descriptions);
  }

  @Test
  @DisplayName("completeTask on empty throws NoSuchElementException")
  void completeEmptyThrows() {
    assertThrows(NoSuchElementException.class, strategy::completeTask);
  }

  @Test
  @DisplayName("activeTask on empty throws NoSuchElementException")
  void activeEmptyThrows() {
    assertThrows(NoSuchElementException.class, strategy::activeTask);
  }

  @Test
  @DisplayName("state survives close and reopen")
  void durability() {
    strategy.addTask("A");
    strategy.addTask("B");
    strategy.completeTask();
    journal.close();

    journal = new FileJournal(temp.resolve("wal.log"), 4096);
    LifoStrategy restored = new LifoStrategy(journal, CODEC);
    assertEquals("A", restored.activeTask());

    Backlog backlog = new Backlog(journal, CODEC);
    List<String> descriptions = backlog.tasks().stream().map(Backlog.Task::description).toList();
    assertEquals(List.of("A"), descriptions);
  }
}
