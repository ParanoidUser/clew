package dev.noid.clew.stack;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.TaskEvent;
import dev.noid.clew.TaskEvent.*;
import dev.noid.clew.codec.JacksonJournalCodec;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.projection.Backlog;
import dev.noid.clew.projection.CompletedTasks;
import dev.noid.clew.stack.ReviseState.Slot;
import dev.noid.clew.journal.file.FileJournal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Stack under test (bottom → top): ["bottom", "middle", "top"]

class ReviseStateTest {

  private static final JournalCodec<TaskEvent> CODEC = new JacksonJournalCodec<>(TaskEvent.class);

  private FileJournal journal;
  private Path scratchFile;

  @BeforeEach
  void setUp(@TempDir Path temp) {
    journal = new FileJournal(temp.resolve("wal.log"), 4096);

    long ts = System.currentTimeMillis();
    String bottomId = UUID.randomUUID().toString();
    String middleId = UUID.randomUUID().toString();
    String topId = UUID.randomUUID().toString();

    journal.append(List.of(
        CODEC.encode(new TaskCreated(ts, bottomId, "bottom")),
        CODEC.encode(new TaskActivated(ts, bottomId)),
        CODEC.encode(new TaskDeactivated(ts, bottomId)),
        CODEC.encode(new TaskCreated(ts, middleId, "middle")),
        CODEC.encode(new TaskActivated(ts, middleId)),
        CODEC.encode(new TaskDeactivated(ts, middleId)),
        CODEC.encode(new TaskCreated(ts, topId, "top")),
        CODEC.encode(new TaskActivated(ts, topId))
    ));
    scratchFile = temp.resolve("revise.tmp");
  }

  @AfterEach
  void tearDown() {
    journal.close();
  }

  @Test
  @DisplayName("main().getFirst() is the stack top (most recently pushed)")
  void topIsFirst() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    assertEquals("top", state.main().getFirst());
  }

  @Test
  @DisplayName("start → main=[top,middle,bottom], temps empty")
  void initialState() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    assertEquals(List.of("top", "middle", "bottom"), state.main());
    assertTrue(state.tempA().isEmpty());
    assertTrue(state.tempB().isEmpty());
  }

  @Test
  @DisplayName("start throws if scratch file already exists")
  void startThrowsIfActive() {
    ReviseState.start(journal, CODEC, scratchFile);
    assertThrows(IllegalStateException.class, () -> ReviseState.start(journal, CODEC, scratchFile));
  }

  @Test
  @DisplayName("move(MAIN→A) moves top of main to tempA")
  void moveMainToTemp() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.move(Slot.MAIN, Slot.A);
    assertEquals(List.of("middle", "bottom"), state.main());
    assertEquals(List.of("top"), state.tempA());
  }

  @Test
  @DisplayName("move(A→MAIN) returns item back to main")
  void moveTempToMain() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.A, Slot.MAIN);
    assertEquals(List.of("top", "middle", "bottom"), state.main());
    assertTrue(state.tempA().isEmpty());
  }

  @Test
  @DisplayName("move from empty stack throws")
  void moveFromEmptyThrows() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    assertThrows(NoSuchElementException.class, () -> state.move(Slot.A, Slot.B));
  }

  @Test
  @DisplayName("move same→same is a no-op")
  void moveSameSlotIsNoop() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.move(Slot.MAIN, Slot.MAIN);
    assertEquals(List.of("top", "middle", "bottom"), state.main());
  }

  @Test
  @DisplayName("edit replaces top of main")
  void editTopMessage() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.edit("X");
    assertEquals(List.of("X", "middle", "bottom"), state.main());
  }

  @Test
  @DisplayName("edit on empty main throws")
  void editEmptyThrows() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.MAIN, Slot.A);
    assertThrows(NoSuchElementException.class, () -> state.edit("X"));
  }

  @Test
  @DisplayName("restore produces identical state to pre-restore")
  void restoreMatchesState() {
    ReviseState before = ReviseState.start(journal, CODEC, scratchFile);
    before.move(Slot.MAIN, Slot.B);
    before.move(Slot.MAIN, Slot.A);

    ReviseState after = ReviseState.restore(journal, CODEC, scratchFile);
    assertEquals(before.main(), after.main());
    assertEquals(before.tempA(), after.tempA());
    assertEquals(before.tempB(), after.tempB());
  }

  @Test
  @DisplayName("restore preserves edit")
  void restorePreservesEdit() {
    ReviseState before = ReviseState.start(journal, CODEC, scratchFile);
    before.edit("edited");

    ReviseState after = ReviseState.restore(journal, CODEC, scratchFile);
    assertEquals(List.of("edited", "middle", "bottom"), after.main());
  }

  @Test
  @DisplayName("commit reorder → replayed state reflects new order, scratch deleted")
  void commitReorder() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.A, Slot.MAIN);
    state.move(Slot.A, Slot.MAIN);
    state.move(Slot.A, Slot.MAIN);
    state.commit();

    assertFalse(Files.exists(scratchFile));
    assertEquals(List.of("top", "middle", "bottom"), replayMain());
  }

  @Test
  @DisplayName("commit with items in temps → those items are dropped")
  void commitDropsTemps() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.move(Slot.MAIN, Slot.A);  // move "top" to tempA
    state.move(Slot.MAIN, Slot.B);  // move "middle" to tempB
    // main=[bottom], tempA=[top], tempB=[middle]
    state.commit();

    assertFalse(Files.exists(scratchFile));
    assertEquals(List.of("bottom"), replayMain());
  }

  @Test
  @DisplayName("commit after edit → edited message in replayed state")
  void commitAfterEdit() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.edit("replaced");  // replaces "top"
    state.commit();

    assertEquals(List.of("replaced", "middle", "bottom"), replayMain());
  }

  @Test
  @DisplayName("cancel → scratch deleted, journal unchanged")
  void cancelLeavesJournalUntouched() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.move(Slot.MAIN, Slot.A);
    state.cancel();

    assertFalse(Files.exists(scratchFile));
    assertEquals(List.of("top", "middle", "bottom"), replayMain());
  }

  @Test
  @DisplayName("commit with dropped items emits TaskDropped, not TaskCompleted")
  void commitWritesDropForDiscardedItems() {
    long positionBeforeRevise = journal.currentPosition();

    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.move(Slot.MAIN, Slot.A);  // discard "top"
    state.commit();

    // read only the events written by commit
    List<TaskEvent> commitEvents;
    try (var stream = journal.openStream(positionBeforeRevise)) {
      commitEvents = stream.map(CODEC::decode).toList();
    }

    long dropCount = commitEvents.stream().filter(e -> e instanceof TaskDropped).count();
    long completedCount = commitEvents.stream().filter(e -> e instanceof TaskCompleted).count();

    assertTrue(dropCount >= 1, "Discarded items should produce TaskDropped events");
    assertEquals(0, completedCount, "No task was completed — no TaskCompleted events expected");
  }

  @Test
  @DisplayName("revise-discarded task does not appear in completed log")
  void discardedTaskMustNotAppearInCompletedLog() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.move(Slot.MAIN, Slot.A);  // discard "top"
    state.commit();

    CompletedTasks completedTasks = new CompletedTasks(journal, CODEC);
    assertEquals(List.of(), completedTasks.tasks());
  }

  @Test
  @DisplayName("pure reorder via revise does not mark tasks as completed")
  void reorderedTasksMustNotAppearInCompletedLog() {
    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.A, Slot.MAIN);
    state.move(Slot.A, Slot.MAIN);
    state.move(Slot.A, Slot.MAIN);
    state.commit();

    CompletedTasks completedTasks = new CompletedTasks(journal, CODEC);
    Backlog backlog = new Backlog(journal, CODEC);
    assertEquals(List.of(), completedTasks.tasks(), "no task was completed — log must be empty");
    assertEquals(3, backlog.tasks().size(), "all three tasks must remain in the backlog");
  }

  private List<String> replayMain() {
    ReviseState replayed = ReviseState.start(journal, CODEC, scratchFile);
    List<String> result = replayed.main();
    replayed.cancel();
    return result;
  }
}
