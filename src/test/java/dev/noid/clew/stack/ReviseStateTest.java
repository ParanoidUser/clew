package dev.noid.clew.stack;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.JournalRecord;
import dev.noid.clew.JournalRecord.Push;
import dev.noid.clew.codec.JacksonJournalCodec;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.stack.ReviseState.Slot;
import dev.noid.clew.journal.file.FileJournal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviseStateTest {

  private static final JournalCodec<JournalRecord> CODEC = new JacksonJournalCodec<>(JournalRecord.class);

  private FileJournal journal;
  private Path scratchFile;

  @BeforeEach
  void setUp(@TempDir Path temp) {
    journal = new FileJournal(temp.resolve("wal.log"), 4096);
    journal.append(List.of(
        CODEC.encode(new Push("bottom")),
        CODEC.encode(new Push("middle")),
        CODEC.encode(new Push("top"))
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
  @DisplayName("commit with dropped items writes Drop records, not Pop records")
  void commitWritesDropForDiscardedItems() {
    long positionBeforeRevise = journal.currentPosition();

    ReviseState state = ReviseState.start(journal, CODEC, scratchFile);
    state.move(Slot.MAIN, Slot.A);  // discard "top"
    state.commit();

    // read only the records written by commit
    List<JournalRecord> commitRecords;
    try (var stream = journal.openStream(positionBeforeRevise)) {
      commitRecords = stream.map(CODEC::decode).toList();
    }

    // original stack had 3 items: 2 kept in main after move, 1 discarded in tempA
    // expect: 3x Pop (clear original stack) + 2x Push (re-push main) + 1x Drop (discard tempA item)
    long dropCount = commitRecords.stream().filter(r -> r instanceof JournalRecord.Drop).count();
    long popCount = commitRecords.stream().filter(r -> r instanceof JournalRecord.Pop).count();

    assertEquals(1, dropCount, "Discarded items should produce Drop records, not Pop");
    assertEquals(2, popCount, "Only kept items should produce Pop records before re-push");
  }

  private List<String> replayMain() {
    ReviseState replayed = ReviseState.start(journal, CODEC, scratchFile);
    List<String> result = replayed.main();
    replayed.cancel();
    return result;
  }
}
