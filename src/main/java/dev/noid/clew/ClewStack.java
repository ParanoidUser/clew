package dev.noid.clew;

import dev.noid.clew.JournalEntry.Drop;
import dev.noid.clew.JournalEntry.Pop;
import dev.noid.clew.JournalEntry.Push;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A durable, thread-safe Stack projection backed by a {@link Journal}. O(1) for reads, O(Delta) for catch-up
 * synchronization.
 */
public class ClewStack {

  private final Deque<String> projection = new ArrayDeque<>();
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final Journal journal;
  private long lastAppliedPosition = -1L;

  public ClewStack(Journal journal) {
    this.journal = journal;
  }

  public void push(String message) throws JournalException {
    long newPosition = journal.append(List.of(new Push(message)));
    syncTo(newPosition);
  }

  public String pop() throws JournalException {
    lock.writeLock().lock();
    try {
      syncToInternal(journal.getEndPosition());

      if (projection.isEmpty()) {
        throw new NoSuchElementException("Stack is empty");
      }

      String message = projection.peekLast();
      long newPosition = journal.append(List.of(new Pop()));
      syncToInternal(newPosition);
      return message;
    } finally {
      lock.writeLock().unlock();
    }
  }

  public String peek() throws JournalException {
    syncTo(journal.getEndPosition());
    lock.readLock().lock();
    try {
      if (projection.isEmpty()) {
        throw new NoSuchElementException("Stack is empty");
      }
      return projection.peekLast();
    } finally {
      lock.readLock().unlock();
    }
  }

  public List<String> list() {
    try {
      syncTo(journal.getEndPosition());
    } catch (JournalException e) {
      throw new RuntimeException(e);
    }
    lock.readLock().lock();
    try {
      return List.copyOf(projection);
    } finally {
      lock.readLock().unlock();
    }
  }

  private void syncTo(long targetPosition) {
    if (targetPosition <= lastAppliedPosition) {
      return;
    }
    lock.writeLock().lock();
    try {
      syncToInternal(targetPosition);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void syncToInternal(long targetPosition) {
    if (targetPosition <= lastAppliedPosition) {
      return;
    }

    try (var stream = journal.openStream(lastAppliedPosition + 1)) {
      stream.forEach(entry -> {
        switch (entry) {
          case Push(String msg) -> projection.addLast(msg);
          case Pop(), Drop() -> {
            if (!projection.isEmpty()) {
              projection.removeLast();
            }
          }
        }
      });
      this.lastAppliedPosition = targetPosition;
    } catch (Exception cause) {
      throw new RuntimeException(cause);
    }
  }
}
