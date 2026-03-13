package dev.noid.clew.workspace;

import dev.noid.clew.journal.JournalException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Coordinates exclusive write access to a project workspace.
 * <p>
 * Uses a {@link FileLock} on a dedicated lock file to ensure that at most one write operation
 * (add, done, plan commit) can modify the journal at any time. The OS automatically releases
 * the lock if the process terminates, preventing stale locks.
 * <p>
 * Read operations do not require a lock and should not acquire one.
 * <p>
 * To determine whether a planning session is active, check for the existence of the project's
 * scratch file — that is the source of truth for plan mode, not this lock.
 */
public final class ProjectLock implements AutoCloseable {

  private final FileChannel channel;
  private final FileLock lock;

  private ProjectLock(FileChannel channel, FileLock lock) {
    this.channel = channel;
    this.lock = lock;
  }

  /**
   * Acquires an exclusive lock on the given lock file.
   * <p>
   * If another process or thread already holds the lock, this method fails immediately
   * rather than blocking.
   *
   * @param lockFile path to the lock file (e.g., {@code project/lock})
   * @return a held lock; must be closed to release
   * @throws JournalException if the lock cannot be acquired
   */
  public static ProjectLock acquire(Path lockFile) {
    try {
      if (lockFile.getParent() != null) {
        java.nio.file.Files.createDirectories(lockFile.getParent());
      }
      FileChannel channel = FileChannel.open(lockFile,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE);
      FileLock fl;
      try {
        fl = channel.tryLock();
      } catch (OverlappingFileLockException e) {
        channel.close();
        throw new JournalException("Project is locked by another operation", e);
      }
      if (fl == null) {
        channel.close();
        throw new JournalException("Project is locked by another process");
      }
      return new ProjectLock(channel, fl);
    } catch (IOException e) {
      throw new JournalException("Failed to acquire project lock: " + lockFile, e);
    }
  }

  @Override
  public void close() {
    try {
      lock.release();
      channel.close();
    } catch (IOException e) {
      throw new JournalException("Failed to release project lock", e);
    }
  }
}
