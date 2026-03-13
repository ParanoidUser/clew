package dev.noid.clew.journal.file;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.journal.file.FileJournal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileJournalTest {

  private static final long MAX_SIZE = 4096;

  @Test
  @DisplayName("new journal has position 0 and empty stream")
  void emptyJournal(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      assertEquals(0, journal.currentPosition());
      assertTrue(journal.openStream(0).toList().isEmpty());
    }
  }

  @Test
  @DisplayName("append and read back single record")
  void appendAndRead(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      byte[] record = "hello".getBytes();
      journal.append(List.of(record));

      List<byte[]> results = journal.openStream(0).toList();
      assertEquals(1, results.size());
      assertArrayEquals(record, results.getFirst());
    }
  }

  @Test
  @DisplayName("batch append preserves order")
  void batchOrder(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      journal.append(List.of("first".getBytes(), "second".getBytes(), "third".getBytes()));

      List<String> results = journal.openStream(0).map(String::new).toList();
      assertEquals(List.of("first", "second", "third"), results);
    }
  }

  @Test
  @DisplayName("multiple appends accumulate")
  void multipleAppends(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      journal.append(List.of("a".getBytes()));
      journal.append(List.of("b".getBytes()));
      journal.append(List.of("c".getBytes()));

      List<String> results = journal.openStream(0).map(String::new).toList();
      assertEquals(List.of("a", "b", "c"), results);
    }
  }

  @Test
  @DisplayName("openStream from append position returns empty (caught up)")
  void caughtUp(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      long pos = journal.append(List.of("a".getBytes(), "b".getBytes()));
      assertTrue(journal.openStream(pos).toList().isEmpty());
    }
  }

  @Test
  @DisplayName("openStream from mid-position skips earlier records")
  void partialReplay(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      long mid = journal.append(List.of("old".getBytes()));
      journal.append(List.of("new".getBytes()));

      List<String> results = journal.openStream(mid).map(String::new).toList();
      assertEquals(List.of("new"), results);
    }
  }

  @Test
  @DisplayName("data survives close and reopen")
  void durability(@TempDir Path temp) {
    Path file = temp.resolve("wal.log");
    try (FileJournal writer = new FileJournal(file, MAX_SIZE)) {
      writer.append(List.of("durable".getBytes()));
    }
    try (FileJournal reader = new FileJournal(file, MAX_SIZE)) {
      List<String> results = reader.openStream(0).map(String::new).toList();
      assertEquals(List.of("durable"), results);
    }
  }

  @Test
  @DisplayName("position is recovered correctly after reopen")
  void positionRecovery(@TempDir Path temp) {
    Path file = temp.resolve("wal.log");
    long posAfterWrite;
    try (FileJournal writer = new FileJournal(file, MAX_SIZE)) {
      posAfterWrite = writer.append(List.of("a".getBytes(), "b".getBytes()));
    }
    try (FileJournal reader = new FileJournal(file, MAX_SIZE)) {
      assertEquals(posAfterWrite, reader.currentPosition());
      assertTrue(reader.openStream(posAfterWrite).toList().isEmpty());
    }
  }

  @Test
  @DisplayName("binary payload round-trips without corruption")
  void binaryPayload(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      byte[] binary = new byte[256];
      for (int i = 0; i < 256; i++) {
        binary[i] = (byte) i;
      }
      journal.append(List.of(binary));

      assertArrayEquals(binary, journal.openStream(0).toList().getFirst());
    }
  }

  @Test
  @DisplayName("openStream with position beyond committed region throws IllegalArgumentException")
  void openStream_beyondCommitted_throws(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      journal.append(List.of("hello".getBytes()));
      long beyond = journal.currentPosition() + 1;
      // FAILS: currently returns an empty stream instead of throwing
      assertThrows(IllegalArgumentException.class, () -> journal.openStream(beyond));
    }
  }

  @Test
  @DisplayName("openStream with position inside a frame throws IllegalArgumentException")
  void openStream_midFrame_throws(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      journal.append(List.of("hello".getBytes()));
      // 5 is inside the first frame, not a valid frame boundary
      // FAILS: currently throws JournalCorruptionException during iteration, not IAE at call site
      assertThrows(IllegalArgumentException.class, () -> journal.openStream(5));
    }
  }

  @Test
  @DisplayName("append with null or empty list throws IllegalArgumentException")
  void rejectsInvalidInput(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      assertThrows(IllegalArgumentException.class, () -> journal.append(null));
      assertThrows(IllegalArgumentException.class, () -> journal.append(List.of()));
    }
  }

  @Test
  @DisplayName("null element in batch is rejected atomically — no partial write")
  void nullElementInBatchRejectedAtomically(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      assertThrows(IllegalArgumentException.class,
          () -> journal.append(Arrays.asList(new byte[]{1, 2, 3}, null, new byte[]{4, 5, 6})));
      assertEquals(0, journal.openStream(0).toList().size());
    }
  }

  @Test
  @DisplayName("empty payload round-trips correctly")
  void emptyPayloadRoundTrip(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      // FAILS: currently throws IllegalArgumentException
      journal.append(List.of(new byte[0]));

      List<byte[]> results = journal.openStream(0).toList();
      assertEquals(1, results.size());
      assertArrayEquals(new byte[0], results.getFirst());
    }
  }

  @Test
  @DisplayName("empty payload survives close and reopen")
  void emptyPayloadDurability(@TempDir Path temp) {
    Path file = temp.resolve("wal.log");
    // FAILS: currently throws IllegalArgumentException
    try (FileJournal writer = new FileJournal(file, MAX_SIZE)) {
      writer.append(List.of("before".getBytes(), new byte[0], "after".getBytes()));
    }
    try (FileJournal reader = new FileJournal(file, MAX_SIZE)) {
      List<byte[]> results = reader.openStream(0).toList();
      assertEquals(3, results.size());
      assertArrayEquals("before".getBytes(), results.get(0));
      assertArrayEquals(new byte[0], results.get(1));
      assertArrayEquals("after".getBytes(), results.get(2));
    }
  }

  @Test
  void append_whenCapacityExceeded_shouldThrowExplicitException(@TempDir Path temp) {
    long maxSize = 64;
    byte[] tooBig = new byte[(int) maxSize];

    try (FileJournal journal = new FileJournal(temp.resolve("wal.log"), maxSize)) {
      // Currently throws IndexOutOfBoundsException from MemorySegment internals
      // Should throw a domain-specific, documented exception
      assertThrows(IllegalStateException.class, () -> journal.append(List.of(tooBig)));
    }
  }

  @Test
  void append_batchThatExceedsMidway_shouldNotPartiallyCommit(@TempDir Path temp) {
    Path filePath = temp.resolve("wal.log");

    long maxSize = 64;
    byte[] fits = new byte[20];
    byte[] doesNotFit = new byte[50];

    try (FileJournal journal = new FileJournal(filePath, maxSize)) {
      long positionBefore = journal.currentPosition();
      assertThrows(IllegalStateException.class, () -> journal.append(List.of(fits, doesNotFit)));

      // FAILS today: committedPosition is still 0 (force was never called),
      // but `fits` bytes are now written to the mmap buffer with no recovery possible
      assertEquals(positionBefore, journal.currentPosition());
    }

    try (FileJournal journal = new FileJournal(filePath, maxSize)) {
      assertEquals(0, journal.currentPosition());
      assertEquals(0, journal.openStream(0).count());
    }
  }

  private FileJournal open(Path dir, String name) {
    return new FileJournal(dir.resolve(name), MAX_SIZE);
  }
}
