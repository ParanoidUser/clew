package dev.noid.clew.file;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.journal.file.FileJournal;
import java.nio.file.Path;
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
  @DisplayName("append with null or empty list throws IllegalArgumentException")
  void rejectsInvalidInput(@TempDir Path temp) {
    try (FileJournal journal = open(temp, "wal.log")) {
      assertThrows(IllegalArgumentException.class, () -> journal.append(null));
      assertThrows(IllegalArgumentException.class, () -> journal.append(List.of()));
    }
  }

  private FileJournal open(Path dir, String name) {
    return new FileJournal(dir.resolve(name), MAX_SIZE);
  }
}
