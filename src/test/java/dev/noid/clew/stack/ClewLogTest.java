package dev.noid.clew.stack;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.JournalRecord;
import dev.noid.clew.JournalRecord.Drop;
import dev.noid.clew.JournalRecord.Pop;
import dev.noid.clew.JournalRecord.Push;
import dev.noid.clew.codec.JacksonJournalCodec;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.file.FileJournal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClewLogTest {

  private static final JournalCodec<JournalRecord> CODEC = new JacksonJournalCodec<>(JournalRecord.class);

  @TempDir
  Path temp;

  private FileJournal journal;
  private ClewLog log;

  @BeforeEach
  void setUp() {
    journal = new FileJournal(temp.resolve("wal.log"), 4096);
    log = new ClewLog(journal, CODEC);
  }

  @AfterEach
  void tearDown() {
    journal.close();
  }

  @Test
  @DisplayName("empty journal → no completed tasks")
  void emptyJournal() {
    assertTrue(log.list().isEmpty());
  }

  @Test
  @DisplayName("push without pop → no completed tasks")
  void pushOnly() {
    journal.append(List.of(CODEC.encode(new Push("A"))));
    assertTrue(log.list().isEmpty());
  }

  @Test
  @DisplayName("push then pop → task appears in log")
  void pushThenPop() {
    journal.append(List.of(CODEC.encode(new Push("A"))));
    journal.append(List.of(CODEC.encode(new Pop())));
    assertEquals(List.of("A"), log.list());
  }

  @Test
  @DisplayName("push then drop → task does NOT appear in log")
  void pushThenDrop() {
    journal.append(List.of(CODEC.encode(new Push("A"))));
    journal.append(List.of(CODEC.encode(new Drop())));
    assertTrue(log.list().isEmpty());
  }

  @Test
  @DisplayName("multiple completions preserve order (oldest first)")
  void multipleCompletions() {
    journal.append(List.of(
        CODEC.encode(new Push("A")),
        CODEC.encode(new Push("B")),
        CODEC.encode(new Pop()),
        CODEC.encode(new Pop())
    ));
    assertEquals(List.of("B", "A"), log.list());
  }

  @Test
  @DisplayName("mixed pop and drop → only popped tasks in log")
  void mixedPopAndDrop() {
    journal.append(List.of(
        CODEC.encode(new Push("A")),
        CODEC.encode(new Push("B")),
        CODEC.encode(new Push("C")),
        CODEC.encode(new Pop()),   // completes C
        CODEC.encode(new Drop()),  // discards B
        CODEC.encode(new Pop())    // completes A
    ));
    assertEquals(List.of("C", "A"), log.list());
  }

  @Test
  @DisplayName("push, pop, push same message, pop → appears twice")
  void sameMessageCompletedTwice() {
    journal.append(List.of(
        CODEC.encode(new Push("A")),
        CODEC.encode(new Pop()),
        CODEC.encode(new Push("A")),
        CODEC.encode(new Pop())
    ));
    assertEquals(List.of("A", "A"), log.list());
  }

  @Test
  @DisplayName("items still on stack don't appear in log")
  void activeItemsExcluded() {
    journal.append(List.of(
        CODEC.encode(new Push("A")),
        CODEC.encode(new Push("B")),
        CODEC.encode(new Pop())    // completes B
    ));
    assertEquals(List.of("B"), log.list());
  }
}
