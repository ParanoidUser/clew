package dev.noid.clew;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.ReviseState.Slot;
import dev.noid.clew.JournalEntry.Push;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviseStateTest {

  private DiskJournal wal;

  @BeforeEach
  void setUp(@TempDir Path temp) throws IOException {
    wal = new DiskJournal(Files.createFile(temp.resolve("wal.log")));
    wal.append(List.of(new Push("A"), new Push("B"), new Push("C")));
  }

  @Test
  void start_throws_if_revise_already_active(@TempDir Path temp) {
    Path scratchFile = temp.resolve("revise.tmp");
    ReviseState.start(wal, scratchFile);
    assertThrows(IllegalStateException.class, () -> ReviseState.start(wal, scratchFile));
  }

  @DisplayName("start([A, B, C]) → main=[A,B,C], tempA=[], tempB=[]")
  @Test
  void initial_state(@TempDir Path temp) {
    Path scratchFile = temp.resolve("revise.tmp");
    ReviseState state = ReviseState.start(wal, scratchFile);
    assertEquals(List.of("A", "B", "C"), state.main());
    assertTrue(state.tempA().isEmpty());
    assertTrue(state.tempB().isEmpty());
  }

  @DisplayName("move(MAIN→A) → top of main goes to tempA")
  @Test
  void move_from_main_to_tempA(@TempDir Path temp) {
    Path scratchFile = temp.resolve("revise.tmp");
    ReviseState state = ReviseState.start(wal, scratchFile);
    state.move(Slot.MAIN, Slot.A);
    assertEquals(List.of("B", "C"), state.main());
    assertEquals(List.of("A"), state.tempA());
    assertTrue(state.tempB().isEmpty());
  }

  @DisplayName("move(A→MAIN) → top of tempA comes back to main")
  @Test
  void move_from_tempA_to_main(@TempDir Path temp) {
    Path scratchFile = temp.resolve("revise.tmp");
    ReviseState state = ReviseState.start(wal, scratchFile);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.A, Slot.MAIN);
    assertEquals(List.of("A", "B", "C"), state.main());
    assertTrue(state.tempA().isEmpty());
    assertTrue(state.tempB().isEmpty());
  }

  @DisplayName("move from empty stack → throws")
  @Test
  void move_from_empty_stack(@TempDir Path temp) {
    Path scratchFile = temp.resolve("revise.tmp");
    ReviseState state = ReviseState.start(wal, scratchFile);
    assertThrows(NoSuchElementException.class, () -> state.move(Slot.A, Slot.B));
  }

  @DisplayName("move from source to the same target")
  @Test
  void move_from_source_to_same_target(@TempDir Path temp) {
    Path scratchFile = temp.resolve("revise.tmp");
    ReviseState state = ReviseState.start(wal, scratchFile);
    assertDoesNotThrow(() -> state.move(Slot.MAIN, Slot.MAIN));
  }

  @DisplayName("edit(\"new\") → top of main message replaced")
  @Test
  void edit_top_main_message(@TempDir Path temp) {
    Path scratchFile = temp.resolve("revise.tmp");
    ReviseState state = ReviseState.start(wal, scratchFile);
    state.edit("X");
    assertEquals(List.of("X", "B", "C"), state.main());
  }

  @DisplayName("edit on empty main → throws")
  @Test
  void edit_when_main_is_empty(@TempDir Path temp) {
    Path scratchFile = temp.resolve("revise.tmp");
    ReviseState state = ReviseState.start(wal, scratchFile);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.MAIN, Slot.A);
    assertThrows(NoSuchElementException.class, () -> state.edit("X"));
  }

  @DisplayName("scratch file written after start → resume() produces identical state")
  @Test
  void resume_revise(@TempDir Path temp) {
    Path scratchFile = temp.resolve("revise.tmp");
    ReviseState before = ReviseState.start(wal, scratchFile);
    before.move(Slot.MAIN, Slot.B);
    before.move(Slot.MAIN, Slot.A);

    assertEquals(List.of("C"), before.main());
    assertEquals(List.of("B"), before.tempA());
    assertEquals(List.of("A"), before.tempB());

    ReviseState after = ReviseState.restore(wal, scratchFile);
    assertEquals(List.of("C"), after.main());
    assertEquals(List.of("B"), after.tempA());
    assertEquals(List.of("A"), after.tempB());
  }

  @DisplayName("commit with full main, empty temps → WAL has correct pop/push sequence, scratch deleted")
  @Test
  void commit_with_full_main(@TempDir Path temp) throws Exception{
    Path scratchFile = temp.resolve("revise.tmp");
    ReviseState state = ReviseState.start(wal, scratchFile);
    state.move(Slot.MAIN, Slot.B);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.B, Slot.MAIN);
    state.move(Slot.A, Slot.B);
    state.move(Slot.A, Slot.MAIN);
    state.move(Slot.B, Slot.MAIN);
    state.commit();

    assertFalse(Files.exists(scratchFile));
    ClewStack verify = new ClewStack(wal);
    assertEquals(List.of("A", "B", "C"), verify.list());
  }

  @DisplayName("commit with items in tempA → those items absent from WAL pushes (dropped)")
  @Test
  void commit_to_drop_temp(@TempDir Path temp) throws Exception {
    Path scratchFile = temp.resolve("revise.tmp");
    ReviseState state = ReviseState.start(wal, scratchFile);

    state.move(Slot.MAIN, Slot.A);
    state.move(Slot.MAIN, Slot.B);
    state.commit();

    assertFalse(Files.exists(scratchFile));
    ClewStack verify = new ClewStack(wal);
    assertEquals(List.of("A"), verify.list());
  }

  @DisplayName("cancel → scratch file deleted, WAL untouched")
  @Test
  void cancel_active_revision(@TempDir Path temp) {
    Path scratchFile = temp.resolve("revise.tmp");
    ReviseState state = ReviseState.start(wal, scratchFile);
    state.move(Slot.MAIN, Slot.A);
    state.cancel();

    assertFalse(Files.exists(scratchFile));
    ClewStack verify = new ClewStack(wal);
    assertEquals(List.of("A", "B", "C"), verify.list()); // bottom on left side
  }
}