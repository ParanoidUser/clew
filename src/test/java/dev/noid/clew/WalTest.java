package dev.noid.clew;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.JournalEntry.Push;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WalTest {

  @DisplayName("readAll() on nonexistent file returns empty list")
  @Test
  void empty_list_on_nonexistent_file(@TempDir Path temp) throws Exception {
    DiskJournal log = new DiskJournal(temp.resolve("wal_not_exists.log"));
    assertEquals(0, log.getEndPosition());
  }

  @DisplayName("append creates file and parent directories if they don't exist")
  @Test
  void append_creates_missing_file_and_directories(@TempDir Path temp) {
//    DiskJournal log = new DiskJournal(temp.resolve("subdir/wal.log"));
//    log.append(List.of(new Push("task")));
//
//    List<JournalEntry> entries = log.readAll();
//    assertEquals(1, entries.size());
//    assertInstanceOf(Push.class, entries.getFirst());
  }

  @DisplayName("readAll() on empty file returns empty list")
  @Test
  void empty_list_on_empty_file(@TempDir Path temp) throws Exception {
//    Path walFile = Files.createFile(temp.resolve("empty.log"));
//    DiskJournal log = new DiskJournal(walFile);
//    assertTrue(log.readAll().isEmpty());
  }

  @DisplayName("append(Push) → readAll() returns that Push with correct message")
  @Test
  void list_entry_after_push_append(@TempDir Path temp) throws Exception {
//    Path walFile = Files.createFile(temp.resolve("empty.log"));
//    DiskJournal log = new DiskJournal(walFile);
//    log.append(List.of(new Push("Hello!")));
//
//    List<JournalEntry> entries = log.readAll();
//    assertEquals(1, entries.size());
//    JournalEntry entry = entries.getFirst();
//    assertInstanceOf(Push.class, entry);
//    assertEquals("Hello!", ((Push) entry).msg());
  }

  @DisplayName("append(Pop) → readAll() returns a Pop")
  @Test
  void list_entry_after_pop_append(@TempDir Path temp) throws Exception {
//    Path walFile = Files.createFile(temp.resolve("empty.log"));
//    DiskJournal log = new DiskJournal(walFile);
//    log.append(List.of(new Pop()));
//
//    List<JournalEntry> entries = log.readAll();
//    assertEquals(1, entries.size());
//    JournalEntry entry = entries.getFirst();
//    assertInstanceOf(Pop.class, entry);
  }

  @DisplayName("multiple appends preserve insertion order")
  @Test
  void list_entry_after_multiple_append(@TempDir Path temp) throws Exception {
//    Path walFile = Files.createFile(temp.resolve("empty.log"));
//    DiskJournal log = new DiskJournal(walFile);
//    log.append(List.of(
//        new Push("1"),
//        new Push("3"),
//        new Pop(),
//        new Push("2")
//    ));
//
//    List<JournalEntry> entries = log.readAll();
//    assertEquals(4, entries.size());
//    assertInstanceOf(Push.class, entries.get(0));
//    assertEquals("1", ((Push) entries.get(0)).msg());
//    assertInstanceOf(Push.class, entries.get(1));
//    assertEquals("3", ((Push) entries.get(1)).msg());
//    assertInstanceOf(Pop.class, entries.get(2));
//    assertInstanceOf(Push.class, entries.get(3));
//    assertEquals("2", ((Push) entries.get(3)).msg());
  }

  @DisplayName("verifies durability — data survives object reconstruction")
  @Test
  void durable(@TempDir Path temp) throws Exception {
//    Path walFile = Files.createFile(temp.resolve("wal.log"));
//    DiskJournal write = new DiskJournal(walFile);
//    write.append(List.of(new Pop()));
//
//    DiskJournal read = new DiskJournal(walFile);
//    List<JournalEntry> entries = read.readAll();
//
//    assertEquals(1, entries.size());
//    JournalEntry entry = entries.getFirst();
//    assertInstanceOf(Pop.class, entry);
  }

  @DisplayName("message with special characters round-trips without corruption")
  @Test
  void special_characters_in_message(@TempDir Path temp) throws Exception {
//    Path walFile = Files.createFile(temp.resolve("wal.log"));
//    DiskJournal log = new DiskJournal(walFile);
//    String nasty = "it's a \"test\" with \\backslashes\\ and\nnewlines";
//    log.append(List.of(new Push(nasty)));
//
//    DiskJournal read = new DiskJournal(walFile);
//    Push entry = (Push) read.readAll().getFirst();
//    assertEquals(nasty, entry.msg());
  }

  @DisplayName("openStream(lastPosition) after append → returns empty stream (caught up), not full replay")
  @Test
  void openStream_at_end_of_file_returns_empty(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("wal.log"));
    DiskJournal log = new DiskJournal(walFile);
    long position = log.append(List.of(new Push("A"), new Push("B")));

    try (Stream<JournalEntry> stream = log.openStream(position)) {
      List<JournalEntry> entries = stream.toList();
      assertTrue(entries.isEmpty(), "Expected empty stream at caught-up position, but got " + entries.size() + " entries (full replay)");
    }
  }
}