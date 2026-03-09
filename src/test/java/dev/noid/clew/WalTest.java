package dev.noid.clew;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.WalEntry.Pop;
import dev.noid.clew.WalEntry.Push;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WalTest {

  @DisplayName("readAll() on nonexistent file returns empty list")
  @Test
  void empty_list_on_nonexistent_file(@TempDir Path temp) {
    Wal log = new Wal(temp.resolve("wal_not_exists.log"));
    assertTrue(log.readAll().isEmpty());
  }

  @DisplayName("append creates file and parent directories if they don't exist")
  @Test
  void append_creates_missing_file_and_directories(@TempDir Path temp) {
    Wal log = new Wal(temp.resolve("subdir/wal.log"));
    log.append(new Push("task"));

    List<WalEntry> entries = log.readAll();
    assertEquals(1, entries.size());
    assertInstanceOf(Push.class, entries.getFirst());
  }

  @DisplayName("readAll() on empty file returns empty list")
  @Test
  void empty_list_on_empty_file(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    Wal log = new Wal(walFile);
    assertTrue(log.readAll().isEmpty());
  }

  @DisplayName("append(Push) → readAll() returns that Push with correct message")
  @Test
  void list_entry_after_push_append(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    Wal log = new Wal(walFile);
    log.append(new Push("Hello!"));

    List<WalEntry> entries = log.readAll();
    assertEquals(1, entries.size());
    WalEntry entry = entries.getFirst();
    assertInstanceOf(Push.class, entry);
    assertEquals("Hello!", ((Push) entry).msg());
  }

  @DisplayName("append(Pop) → readAll() returns a Pop")
  @Test
  void list_entry_after_pop_append(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    Wal log = new Wal(walFile);
    log.append(new Pop());

    List<WalEntry> entries = log.readAll();
    assertEquals(1, entries.size());
    WalEntry entry = entries.getFirst();
    assertInstanceOf(Pop.class, entry);
  }

  @DisplayName("multiple appends preserve insertion order")
  @Test
  void list_entry_after_multiple_append(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    Wal log = new Wal(walFile);
    log.append(new Push("1"));
    log.append(new Push("3"));
    log.append(new Pop());
    log.append(new Push("2"));

    List<WalEntry> entries = log.readAll();
    assertEquals(4, entries.size());
    assertInstanceOf(Push.class, entries.get(0));
    assertEquals("1", ((Push) entries.get(0)).msg());
    assertInstanceOf(Push.class, entries.get(1));
    assertEquals("3", ((Push) entries.get(1)).msg());
    assertInstanceOf(Pop.class, entries.get(2));
    assertInstanceOf(Push.class, entries.get(3));
    assertEquals("2", ((Push) entries.get(3)).msg());
  }

  @DisplayName("verifies durability — data survives object reconstruction")
  @Test
  void durable(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("wal.log"));
    Wal write = new Wal(walFile);
    write.append(new Pop());

    Wal read = new Wal(walFile);
    List<WalEntry> entries = read.readAll();

    assertEquals(1, entries.size());
    WalEntry entry = entries.getFirst();
    assertInstanceOf(Pop.class, entry);
  }

  @DisplayName("message with special characters round-trips without corruption")
  @Test
  void special_characters_in_message(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("wal.log"));
    Wal log = new Wal(walFile);
    String nasty = "it's a \"test\" with \\backslashes\\ and\nnewlines";
    log.append(new Push(nasty));

    Wal read = new Wal(walFile);
    Push entry = (Push) read.readAll().getFirst();
    assertEquals(nasty, entry.msg());
  }
}