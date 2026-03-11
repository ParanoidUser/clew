package dev.noid.clew.stack;

import dev.noid.clew.JournalRecord;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.JournalException;
import dev.noid.clew.JournalRecord.Drop;
import dev.noid.clew.JournalRecord.Pop;
import dev.noid.clew.JournalRecord.Push;
import dev.noid.clew.journal.Journal;
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
  private final JournalCodec<JournalRecord> codec;
  private long lastSyncPosition = 0;

  public ClewStack(Journal journal, JournalCodec<JournalRecord> codec) {
    this.journal = journal;
    this.codec = codec;
  }

  public void push(String message) throws JournalException {
    long newPosition = journal.append(List.of(codec.encode(new Push(message))));
    syncTo(newPosition);
  }

  public String pop() throws JournalException {
    lock.writeLock().lock();
    try {
      syncToInternal(journal.currentPosition());

      if (projection.isEmpty()) {
        throw new NoSuchElementException("Stack is empty");
      }

      String message = projection.peekLast();
      long newPosition = journal.append(List.of(codec.encode(new Pop())));
      syncToInternal(newPosition);
      return message;
    } finally {
      lock.writeLock().unlock();
    }
  }

  public String peek() throws JournalException {
    syncTo(journal.currentPosition());
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
      syncTo(journal.currentPosition());
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
    if (targetPosition <= lastSyncPosition) {
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
    if (targetPosition <= lastSyncPosition) {
      return;
    }

    try (var stream = journal.openStream(lastSyncPosition)) {
      stream.map(codec::decode).forEach(entry -> {
        switch (entry) {
          case Push(String msg) -> projection.addLast(msg);
          case Pop(), Drop() -> {
            if (!projection.isEmpty()) {
              projection.removeLast();
            }
          }
        }
      });
      this.lastSyncPosition = targetPosition;
    } catch (Exception cause) {
      throw new RuntimeException(cause);
    }
  }
}
