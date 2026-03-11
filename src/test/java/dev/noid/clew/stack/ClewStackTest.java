package dev.noid.clew.stack;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.JournalRecord;
import dev.noid.clew.codec.JacksonJournalCodec;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.file.FileJournal;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClewStackTest {

  private static final JournalCodec<JournalRecord> CODEC = new JacksonJournalCodec<>(JournalRecord.class);
  private static final long MAX_SIZE = 4096;

  @TempDir
  Path temp;

  private FileJournal journal;
  private ClewStack stack;

  @BeforeEach
  void setUp() {
    journal = new FileJournal(temp.resolve("wal.log"), MAX_SIZE);
    stack = new ClewStack(journal, CODEC);
  }

  @AfterEach
  void tearDown() {
    journal.close();
  }

  @Test
  @DisplayName("new stack is empty")
  void emptyStack() {
    assertTrue(stack.list().isEmpty());
  }

  @Test
  @DisplayName("push then peek returns pushed value")
  void pushAndPeek() {
    stack.push("A");
    assertEquals("A", stack.peek());
  }

  @Test
  @DisplayName("peek returns most recent push")
  void peekReturnsMostRecent() {
    stack.push("A");
    stack.push("B");
    assertEquals("B", stack.peek());
  }

  @Test
  @DisplayName("pop returns most recent push")
  void popReturnsMostRecent() {
    stack.push("A");
    stack.push("B");
    assertEquals("B", stack.pop());
  }

  @Test
  @DisplayName("pop removes top, peek sees next")
  void popThenPeek() {
    stack.push("A");
    stack.push("B");
    stack.pop();
    assertEquals("A", stack.peek());
  }

  @Test
  @DisplayName("push then pop leaves stack empty")
  void pushPopEmpty() {
    stack.push("A");
    stack.pop();
    assertTrue(stack.list().isEmpty());
  }

  @Test
  @DisplayName("list returns bottom-to-top order")
  void listOrder() {
    stack.push("A");
    stack.push("B");
    assertEquals(List.of("A", "B"), stack.list());
  }

  @Test
  @DisplayName("pop on empty throws NoSuchElementException")
  void popEmptyThrows() {
    assertThrows(NoSuchElementException.class, stack::pop);
  }

  @Test
  @DisplayName("peek on empty throws NoSuchElementException")
  void peekEmptyThrows() {
    assertThrows(NoSuchElementException.class, stack::peek);
  }

  @Test
  @DisplayName("state survives close and reopen")
  void durability() {
    stack.push("A");
    stack.push("B");
    stack.pop();
    journal.close();

    journal = new FileJournal(temp.resolve("wal.log"), MAX_SIZE);
    ClewStack restored = new ClewStack(journal, CODEC);
    assertEquals(List.of("A"), restored.list());
    assertEquals("A", restored.peek());
  }
}
