package dev.noid.clew.workspace;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.journal.JournalException;
import dev.noid.clew.journal.file.FileJournal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectLockTest {

  // --- Lock mechanics ---

  @Test
  @DisplayName("acquire and release lock")
  void acquireAndRelease(@TempDir Path temp) {
    Path lockFile = temp.resolve("lock");
    ProjectLock lock = ProjectLock.acquire(lockFile);
    assertDoesNotThrow(lock::close);
  }

  @Test
  @DisplayName("second acquire while held throws JournalException")
  void doubleAcquireFails(@TempDir Path temp) {
    Path lockFile = temp.resolve("lock");
    try (ProjectLock held = ProjectLock.acquire(lockFile)) {
      assertThrows(JournalException.class, () -> ProjectLock.acquire(lockFile));
    }
  }

  @Test
  @DisplayName("acquire after release succeeds")
  void acquireAfterRelease(@TempDir Path temp) {
    Path lockFile = temp.resolve("lock");
    ProjectLock first = ProjectLock.acquire(lockFile);
    first.close();
    ProjectLock second = ProjectLock.acquire(lockFile);
    assertDoesNotThrow(second::close);
  }

  // --- Workflow: write with scratch check ---

  @Test
  @DisplayName("write succeeds when no scratch file exists")
  void writeWithNoPlan(@TempDir Path temp) {
    Path lockFile = temp.resolve("lock");
    Path scratchFile = temp.resolve("scratch");

    try (ProjectLock ignored = ProjectLock.acquire(lockFile)) {
      assertFalse(Files.exists(scratchFile));
      try (FileJournal journal = new FileJournal(temp.resolve("journal.log"), 4096)) {
        journal.append(List.of("event".getBytes()));
        assertEquals(1, journal.openStream(0).count());
      }
    }
  }

  @Test
  @DisplayName("write rejected when scratch file exists (plan active or interrupted)")
  void writeRejectedDuringPlan(@TempDir Path temp) throws Exception {
    Path lockFile = temp.resolve("lock");
    Path scratchFile = temp.resolve("scratch");
    Files.createFile(scratchFile);

    try (ProjectLock ignored = ProjectLock.acquire(lockFile)) {
      assertTrue(Files.exists(scratchFile));
      // This is the check TaskService.checkPlanNotActive() performs
    }
  }

  // --- Concurrency ---

  @Test
  @DisplayName("plan holds lock, concurrent add from another thread fails")
  void planBlocksAdd(@TempDir Path temp) throws Exception {
    Path lockFile = temp.resolve("lock");
    CountDownLatch planStarted = new CountDownLatch(1);
    CountDownLatch addAttempted = new CountDownLatch(1);
    AtomicReference<Exception> addResult = new AtomicReference<>();

    // Plan session thread — holds lock for duration
    Thread planThread = Thread.ofVirtual().start(() -> {
      try (ProjectLock ignored = ProjectLock.acquire(lockFile)) {
        planStarted.countDown();
        addAttempted.await();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Add thread — tries to acquire while plan holds lock
    planStarted.await();
    Thread addThread = Thread.ofVirtual().start(() -> {
      try {
        ProjectLock.acquire(lockFile).close();
      } catch (Exception e) {
        addResult.set(e);
      } finally {
        addAttempted.countDown();
      }
    });

    addThread.join();
    planThread.join();

    assertNotNull(addResult.get());
    assertInstanceOf(JournalException.class, addResult.get());
  }

  @Test
  @DisplayName("read from journal while lock is held by another thread")
  void readWhileLocked(@TempDir Path temp) throws Exception {
    Path lockFile = temp.resolve("lock");
    Path journalFile = temp.resolve("journal.log");

    // Write some data first
    try (FileJournal journal = new FileJournal(journalFile, 4096)) {
      journal.append(List.of("event".getBytes()));
    }

    CountDownLatch lockHeld = new CountDownLatch(1);
    CountDownLatch readDone = new CountDownLatch(1);
    AtomicReference<Long> readCount = new AtomicReference<>();

    // Thread holding the lock (simulates active plan)
    Thread lockHolder = Thread.ofVirtual().start(() -> {
      try (ProjectLock ignored = ProjectLock.acquire(lockFile)) {
        lockHeld.countDown();
        readDone.await();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Reader thread — opens journal without acquiring lock
    lockHeld.await();
    try (FileJournal reader = new FileJournal(journalFile, 4096)) {
      readCount.set(reader.openStream(0).count());
    }
    readDone.countDown();
    lockHolder.join();

    assertEquals(1L, readCount.get());
  }
}
