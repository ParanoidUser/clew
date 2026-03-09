package dev.noid.clew;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandHandlerTest {

  @TempDir
  Path temp;

  private ClewStack stack;
  private ByteArrayOutputStream out;
  private ByteArrayOutputStream err;

  @BeforeEach
  void setUp() throws Exception {
    stack = new ClewStack(new Wal(Files.createFile(temp.resolve("wal.log"))));
    out = new ByteArrayOutputStream();
    err = new ByteArrayOutputStream();
  }

  private int invoke(String... args) {
    ReviseUi noopUi = (state, s) -> {};
    return new CommandHandler(args, stack, temp.resolve("revise.tmp"), noopUi,
        new PrintStream(out), new PrintStream(err)).invoke();
  }

  private String stdout() {
    return out.toString();
  }

  private String stderr() {
    return err.toString();
  }

  @DisplayName("push → exit 0, no output")
  @Test
  void push() {
    int code = invoke("push", "task A");
    assertEquals(0, code);
    assertTrue(stdout().isEmpty());
    assertTrue(stderr().isEmpty());
  }

  @DisplayName("push then peek → exit 0, prints message")
  @Test
  void push_then_peek() {
    invoke("push", "task A");
    int code = invoke("peek");
    assertEquals(0, code);
    assertEquals("task A" + System.lineSeparator(), stdout());
  }

  @DisplayName("push then pop → exit 0, prints popped message")
  @Test
  void push_then_pop() {
    invoke("push", "task A");
    int code = invoke("pop");
    assertEquals(0, code);
    assertEquals("task A" + System.lineSeparator(), stdout());
  }

  @DisplayName("pop on empty → exit 1, stderr contains 'empty'")
  @Test
  void pop_on_empty() {
    int code = invoke("pop");
    assertEquals(1, code);
    assertTrue(stderr().contains("empty"));
    assertTrue(stdout().isEmpty());
  }

  @DisplayName("peek on empty → exit 1, stderr contains 'empty'")
  @Test
  void peek_on_empty() {
    int code = invoke("peek");
    assertEquals(1, code);
    assertTrue(stderr().contains("empty"));
    assertTrue(stdout().isEmpty());
  }

  @DisplayName("ls on empty → exit 0, no output")
  @Test
  void ls_on_empty() {
    int code = invoke("ls");
    assertEquals(0, code);
    assertTrue(stdout().isEmpty());
  }

  @DisplayName("push A, push B, ls → [2] B then [1] A")
  @Test
  void ls_ordering() {
    invoke("push", "A");
    invoke("push", "B");
    int code = invoke("ls");
    assertEquals(0, code);
    assertEquals("[2] B" + System.lineSeparator() + "[1] A" + System.lineSeparator(), stdout());
  }

  @DisplayName("push without message → exit 1, stderr contains 'push requires a message'")
  @Test
  void push_without_message() {
    int code = invoke("push");
    assertEquals(1, code);
    assertTrue(stderr().contains("push requires a message"));
    assertTrue(stdout().isEmpty());
  }

  @DisplayName("unknown command → exit 1, stderr contains 'unknown command'")
  @Test
  void unknown_command() {
    int code = invoke("foobar");
    assertEquals(1, code);
    assertTrue(stderr().contains("unknown command"));
    assertTrue(stdout().isEmpty());
  }

  @DisplayName("no args → exit 1, stderr is not empty")
  @Test
  void no_args() {
    int code = invoke();
    assertEquals(1, code);
    assertFalse(stderr().isEmpty());
    assertTrue(stdout().isEmpty());
  }

  @DisplayName("push/pop/peek/ls blocked when revise.tmp exists → exit 1, stderr contains 'revise in progress'")
  @Test
  void commands_blocked_during_revise(@TempDir Path temp2) throws IOException {
    ClewStack s = new ClewStack(new Wal(Files.createFile(temp2.resolve("wal.log"))));
    s.push("A");
    Path scratch = temp2.resolve("revise.tmp");
    ReviseState.start(s.list(), scratch);

    ReviseUi noopUi = (state, st) -> {};
    for (String cmd : new String[]{"push", "pop", "peek", "ls"}) {
      var localOut = new ByteArrayOutputStream();
      var localErr = new ByteArrayOutputStream();
      int code = new CommandHandler(
          new String[]{cmd}, s, scratch, noopUi,
          new PrintStream(localOut), new PrintStream(localErr)).invoke();
      assertEquals(1, code, "expected exit 1 for: " + cmd);
      assertTrue(localErr.toString().contains("revise in progress"), "expected revise message for: " + cmd);
    }
  }

  @DisplayName("revise on empty stack → exit 1, stderr not empty")
  @Test
  void revise_on_empty_stack() {
    int code = invoke("revise");
    assertEquals(1, code);
    assertFalse(stderr().isEmpty());
  }
}
