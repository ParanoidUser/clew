package dev.noid.clew;

import dev.noid.clew.WalEntry.Pop;
import dev.noid.clew.WalEntry.Push;
import java.util.ArrayDeque;
import java.util.List;
import java.util.NoSuchElementException;

public class ClewStack {

  private final ArrayDeque<String> deque = new ArrayDeque<>();
  private final Wal wal;

  public ClewStack(Wal wal) {
    this.wal = wal;

    for (WalEntry entry : wal.readAll()) {
      if (entry instanceof Push(String msg)) {
        deque.push(msg);
      } else {
        deque.pop();
      }
    }
  }

  public void push(String message) {
    wal.append(new Push(message));
    deque.push(message);
  }

  public String pop() {
    String message = deque.pop();
    wal.append(new Pop());
    return message;
  }

  public String peek() {
    String message = deque.peek();
    if (message == null) {
      throw new NoSuchElementException();
    }
    return message;
  }

  public List<String> list() {
    return deque.stream().toList();
  }
}
