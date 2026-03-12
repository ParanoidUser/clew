package dev.noid.clew.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noid.clew.TaskEvent;
import dev.noid.clew.codec.JacksonJournalCodec;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.file.FileJournal;
import dev.noid.clew.projection.Backlog;
import dev.noid.clew.projection.CompletedTasks;
import dev.noid.clew.strategy.LifoStrategy;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Disabled
class CommandHandlerTest {

  private static final JournalCodec<TaskEvent> CODEC = new JacksonJournalCodec<>(TaskEvent.class);

  private static final ReviseHandler NO_REVISE = new ReviseHandler() {
    @Override
    public boolean isInProgress() {
      return false;
    }

    @Override
    public void run() { /* noop */ }
  };

  private static final ReviseHandler ACTIVE_REVISE = new ReviseHandler() {
    @Override
    public boolean isInProgress() {
      return true;
    }

    @Override
    public void run() { /* noop */ }
  };

  @TempDir
  Path temp;

  private FileJournal journal;
  private LifoStrategy strategy;
  private Backlog backlog;
  private CompletedTasks completedTasks;
  private ByteArrayOutputStream out;
  private ByteArrayOutputStream err;

  @BeforeEach
  void setUp() {
    journal = new FileJournal(temp.resolve("wal.log"), 4096);
    strategy = new LifoStrategy(journal, CODEC);
    backlog = new Backlog(journal, CODEC);
    completedTasks = new CompletedTasks(journal, CODEC);
    out = new ByteArrayOutputStream();
    err = new ByteArrayOutputStream();
  }

  @AfterEach
  void tearDown() {
    journal.close();
  }

  @Test
  @DisplayName("push → exit 0, no output")
  void push() {
    assertEquals(0, invoke(NO_REVISE, "push", "task A"));
    assertTrue(stdout().isEmpty());
    assertTrue(stderr().isEmpty());
  }

  @Test
  @DisplayName("push then peek → exit 0, prints message")
  void pushThenPeek() {
    invoke(NO_REVISE, "push", "task A");
    assertEquals(0, invoke(NO_REVISE, "peek"));
    assertEquals("task A" + System.lineSeparator(), stdout());
  }

  @Test
  @DisplayName("push then pop → exit 0, prints popped message")
  void pushThenPop() {
    invoke(NO_REVISE, "push", "task A");
    assertEquals(0, invoke(NO_REVISE, "pop"));
    assertEquals("task A" + System.lineSeparator(), stdout());
  }

  @Test
  @DisplayName("pop on empty → exit 1, stderr contains 'empty'")
  void popOnEmpty() {
    assertEquals(1, invoke(NO_REVISE, "pop"));
    assertTrue(stderr().contains("empty"));
    assertTrue(stdout().isEmpty());
  }

  @Test
  @DisplayName("peek on empty → exit 1, stderr contains 'empty'")
  void peekOnEmpty() {
    assertEquals(1, invoke(NO_REVISE, "peek"));
    assertTrue(stderr().contains("empty"));
    assertTrue(stdout().isEmpty());
  }

  @Test
  @DisplayName("ls on empty → exit 0, no output")
  void lsOnEmpty() {
    assertEquals(0, invoke(NO_REVISE, "ls"));
    assertTrue(stdout().isEmpty());
  }

  @Test
  @DisplayName("push A, push B, ls → top item first, both present")
  void lsOrdering() {
    invoke(NO_REVISE, "push", "A");
    invoke(NO_REVISE, "push", "B");
    assertEquals(0, invoke(NO_REVISE, "ls"));
    String output = stdout();
    assertTrue(output.indexOf("B") < output.indexOf("A"), "top item (B) should appear before A");
    assertTrue(output.contains("[2]") && output.contains("[1]"), "items should be numbered");
  }

  @Test
  @DisplayName("push without message → exit 1")
  void pushWithoutMessage() {
    assertEquals(1, invoke(NO_REVISE, "push"));
    assertTrue(stderr().contains("push requires a message"));
    assertTrue(stdout().isEmpty());
  }

  @Test
  @DisplayName("unknown command → exit 1")
  void unknownCommand() {
    assertEquals(1, invoke(NO_REVISE, "foobar"));
    assertTrue(stderr().contains("unknown command"));
    assertTrue(stdout().isEmpty());
  }

  @Test
  @DisplayName("no args → exit 1")
  void noArgs() {
    assertEquals(1, invoke(NO_REVISE));
    assertFalse(stderr().isEmpty());
    assertTrue(stdout().isEmpty());
  }

  @Test
  @DisplayName("log on empty → exit 0, prints 'no completed tasks'")
  void logOnEmpty() {
    assertEquals(0, invoke(NO_REVISE, "log"));
    assertTrue(stdout().contains("no completed tasks"));
  }

  @Test
  @DisplayName("push then pop then log → exit 0, shows completed task with checkmark")
  void logShowsCompletedTask() {
    invoke(NO_REVISE, "push", "A");
    invoke(NO_REVISE, "pop");
    out.reset();
    assertEquals(0, invoke(NO_REVISE, "log"));
    assertTrue(stdout().contains("\u2713"), "expected checkmark in log output");
    assertTrue(stdout().contains("A"), "expected task message in log output");
  }

  @Test
  @DisplayName("log is blocked when revise in progress")
  void logBlockedDuringRevise() {
    assertEquals(1, invoke(ACTIVE_REVISE, "log"));
    assertTrue(stderr().contains("revise in progress"));
  }

  @Test
  @DisplayName("commands blocked when revise in progress")
  void blockedDuringRevise() {
    for (String cmd : new String[]{"push", "pop", "peek", "ls"}) {
      out.reset();
      err.reset();
      assertEquals(1, invoke(ACTIVE_REVISE, cmd), "expected exit 1 for: " + cmd);
      assertTrue(stderr().contains("revise in progress"), "expected revise message for: " + cmd);
    }
  }

  @Test
  @DisplayName("revise on empty stack → exit 1")
  void reviseOnEmptyStack() {
    assertEquals(1, invoke(NO_REVISE, "revise"));
    assertFalse(stderr().isEmpty());
  }

  private int invoke(ReviseHandler revise, String... args) {

    // fixme: replaced by ConsolePresenter + TaskService
    //return new CommandHandler(args, strategy, backlog, completedTasks, revise,
    //    new PrintStream(out), new PrintStream(err)).invoke();
    return 0;
  }

  private String stdout() {
    return out.toString();
  }

  private String stderr() {
    return err.toString();
  }
}
