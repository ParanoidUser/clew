package dev.noid.clew.journal.file;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileJournalTest {

  private FileJournal journal;

  @BeforeEach
  void setUp(@TempDir Path temp) {
    this.journal = new FileJournal(temp.resolve("journal.log"), 64);
  }

  @AfterEach
  void tearDown() {
    journal.close();
  }

  @Test
  @DisplayName("new journal has position 0 and empty stream")
  void emptyJournal() {
    assertEquals(0, journal.currentPosition());
    assertTrue(journal.openStream(0).toList().isEmpty());
  }

  @Test
  @DisplayName("append and read back single record")
  void appendAndRead() {
    byte[] record = "hello".getBytes();
    journal.append(List.of(record));

    List<byte[]> results = journal.openStream(0).toList();
    assertEquals(1, results.size());
    assertArrayEquals(record, results.getFirst());
  }

  @Test
  @DisplayName("batch append preserves order")
  void batchOrder() {
    journal.append(List.of("first".getBytes(), "second".getBytes(), "third".getBytes()));

    List<String> results = journal.openStream(0).map(String::new).toList();
    assertEquals(List.of("first", "second", "third"), results);
  }

  @Test
  @DisplayName("multiple appends accumulate")
  void multipleAppends() {
    journal.append(List.of("a".getBytes()));
    journal.append(List.of("b".getBytes()));
    journal.append(List.of("c".getBytes()));

    List<String> results = journal.openStream(0).map(String::new).toList();
    assertEquals(List.of("a", "b", "c"), results);
  }

  @Test
  @DisplayName("openStream from append position returns empty (caught up)")
  void caughtUp() {
    long pos = journal.append(List.of("a".getBytes(), "b".getBytes()));
    assertTrue(journal.openStream(pos).toList().isEmpty());
  }

  @Test
  @DisplayName("openStream from mid-position skips earlier records")
  void partialReplay() {
    long mid = journal.append(List.of("old".getBytes()));
    journal.append(List.of("new".getBytes()));

    List<String> results = journal.openStream(mid).map(String::new).toList();
    assertEquals(List.of("new"), results);
  }

  @Test
  @DisplayName("data survives close and reopen")
  void durability(@TempDir Path temp) {
    Path file = temp.resolve("temp.log");
    try (FileJournal writer = new FileJournal(file, 128)) {
      writer.append(List.of("durable".getBytes()));
    }
    try (FileJournal reader = new FileJournal(file, 128)) {
      List<String> results = reader.openStream(0).map(String::new).toList();
      assertEquals(List.of("durable"), results);
    }
  }

  @Test
  @DisplayName("position is recovered correctly after reopen")
  void positionRecovery(@TempDir Path temp) {
    Path file = temp.resolve("temp.log");
    long lastPosition;
    try (FileJournal writer = new FileJournal(file, 128)) {
      lastPosition = writer.append(List.of("a".getBytes(), "b".getBytes()));
    }
    try (FileJournal reader = new FileJournal(file, 128)) {
      assertEquals(lastPosition, reader.currentPosition());
      assertTrue(reader.openStream(lastPosition).toList().isEmpty());
    }
  }

  @Test
  @DisplayName("binary payload round-trips without corruption")
  void binaryPayload(@TempDir Path temp) {
    byte[] binary = new byte[256];
    for (int i = 0; i < 256; i++) {
      binary[i] = (byte) i;
    }

    Path file = temp.resolve("bin.log");
    try (FileJournal writer = new FileJournal(file, 268)) {
      writer.append(List.of(binary));
      assertArrayEquals(binary, writer.openStream(0).toList().getFirst());
    }
  }

  @Test
  @DisplayName("openStream with position beyond committed region throws IllegalArgumentException")
  void openStream_beyondCommitted_throws() {
    journal.append(List.of("hello".getBytes()));
    long beyond = journal.currentPosition() + 1;
    assertThrows(IllegalArgumentException.class, () -> journal.openStream(beyond));
  }

  @Test
  @DisplayName("openStream with position inside a frame throws IllegalArgumentException")
  void openStream_midFrame_throws() {
    journal.append(List.of("hello".getBytes()));
    assertThrows(IllegalArgumentException.class, () -> journal.openStream(5));
  }

  @Test
  @DisplayName("append with null or empty list throws IllegalArgumentException")
  void rejectsInvalidInput() {
    assertThrows(IllegalArgumentException.class, () -> journal.append(null));
    assertThrows(IllegalArgumentException.class, () -> journal.append(List.of()));
  }

  @Test
  @DisplayName("null element in batch is rejected atomically - no partial write")
  void nullElementInBatchRejectedAtomically() {
    assertThrows(IllegalArgumentException.class,
        () -> journal.append(Arrays.asList("a".getBytes(), null, "c".getBytes())));
    assertEquals(0, journal.openStream(0).toList().size());
  }

  @Test
  @DisplayName("empty payload round-trips correctly")
  void emptyPayloadRoundTrip() {
    journal.append(List.of(new byte[0]));

    List<byte[]> results = journal.openStream(0).toList();
    assertEquals(1, results.size());
    assertArrayEquals(new byte[0], results.getFirst());
  }

  @Test
  @DisplayName("empty payload survives close and reopen")
  void emptyPayloadDurability(@TempDir Path temp) {
    Path file = temp.resolve("temp.log");
    try (FileJournal writer = new FileJournal(file, 128)) {
      writer.append(List.of("before".getBytes(), new byte[0], "after".getBytes()));
    }
    try (FileJournal reader = new FileJournal(file, 128)) {
      List<byte[]> results = reader.openStream(0).toList();
      assertEquals(3, results.size());
      assertArrayEquals("before".getBytes(), results.get(0));
      assertArrayEquals(new byte[0], results.get(1));
      assertArrayEquals("after".getBytes(), results.get(2));
    }
  }

  @Test
  @DisplayName("append beyond capacity should throw")
  void capacityExceeded() {
    byte[] tooBig = new byte[53]; // 53 bytes > maxSize 64 bytes - overhead 12 bytes
    assertThrows(IllegalStateException.class, () -> journal.append(List.of(tooBig)));
  }

  @Test
  @DisplayName("batch append beyond capacity should not partially commit")
  void batchCapacityExceeded() {
    long end = journal.append(List.of("hello".getBytes(), "hola".getBytes()));
    assertEquals(List.of("hello", "hola"), journal.openStream(0).map(String::new).toList());

    assertThrows(IllegalStateException.class, () -> journal.append(List.of("avast".getBytes(), "ahoy".getBytes())));
    assertEquals(end, journal.currentPosition());

    journal.append(List.of("hej".getBytes(), "hi".getBytes()));
    assertEquals(List.of("hej", "hi"), journal.openStream(end).map(String::new).toList());
  }
}
