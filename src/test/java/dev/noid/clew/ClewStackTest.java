package dev.noid.clew;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClewStackTest {

  @DisplayName("new stack on empty WAL → isEmpty()")
  @Test
  void empty_stack(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    DiskJournal log = new DiskJournal(walFile);
    ClewStack stack = new ClewStack(log);
    assertTrue(stack.list().isEmpty());
  }

  @DisplayName("push(\"A\") → peek() returns \"A\"")
  @Test
  void peek_on_last(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    DiskJournal log = new DiskJournal(walFile);
    ClewStack stack = new ClewStack(log);
    stack.push("A");
    assertEquals("A", stack.peek());
  }

  @DisplayName("push(\"A\"), push(\"B\") → peek() returns \"B\"  (top is most recent)")
  @Test
  void top_is_most_Recent(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    DiskJournal log = new DiskJournal(walFile);
    ClewStack stack = new ClewStack(log);
    stack.push("A");
    stack.push("B");
    assertEquals("B", stack.peek());
  }

  @DisplayName("push(\"A\"), push(\"B\") → returns [\"B\", \"A\"]")
  @Test
  void list(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    DiskJournal log = new DiskJournal(walFile);
    ClewStack stack = new ClewStack(log);
    stack.push("A");
    stack.push("B");
    assertEquals("B", stack.pop());
    assertEquals("A", stack.pop());
  }

  @DisplayName("push(\"A\"), pop() → returns \"A\", stack is now empty")
  @Test
  void clean_stack(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    DiskJournal log = new DiskJournal(walFile);
    ClewStack stack = new ClewStack(log);
    stack.push("A");
    assertEquals("A", stack.pop());
    assertTrue(stack.list().isEmpty());
  }

  @DisplayName("push(\"A\"), push(\"B\") → returns \"A, B\", stack is not empty")
  @Test
  void dirty_stack(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    DiskJournal log = new DiskJournal(walFile);
    ClewStack stack = new ClewStack(log);
    stack.push("A");
    stack.push("B");
    assertEquals(List.of("A", "B"), stack.list()); // bottom on left side
  }

  @DisplayName("push(\"A\"), push(\"B\"), pop() → returns \"B\", peek() returns \"A\"")
  @Test
  void ordering(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    DiskJournal log = new DiskJournal(walFile);
    ClewStack stack = new ClewStack(log);
    stack.push("A");
    stack.push("B");
    assertEquals("B", stack.pop());
    assertEquals("A", stack.peek());
  }

  @DisplayName("pop() on empty stack → throws NoSuchElementException")
  @Test
  void throwNSEE_on_empty_stack_pop(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    DiskJournal log = new DiskJournal(walFile);
    ClewStack stack = new ClewStack(log);
    assertThrows(NoSuchElementException.class, stack::pop);
  }

  @DisplayName("peek() on empty stack → throws NSEE")
  @Test
  void throwNSEE_on_empty_stack_peek(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    DiskJournal log = new DiskJournal(walFile);
    ClewStack stack = new ClewStack(log);
    assertThrows(NoSuchElementException.class, stack::peek);
  }

  @DisplayName("push(\"A\") with one instance, new instance on same WAL → peek() returns \"A\"")
  @Test
  void durable(@TempDir Path temp) throws Exception {
    Path walFile = Files.createFile(temp.resolve("empty.log"));
    ClewStack write = new ClewStack(new DiskJournal(walFile));
    write.push("A");

    ClewStack read = new ClewStack(new DiskJournal(walFile));
    assertEquals("A", read.peek());
  }
}