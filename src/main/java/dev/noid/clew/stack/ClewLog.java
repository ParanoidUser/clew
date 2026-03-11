package dev.noid.clew.stack;

import dev.noid.clew.JournalRecord;
import dev.noid.clew.JournalRecord.Drop;
import dev.noid.clew.JournalRecord.Pop;
import dev.noid.clew.JournalRecord.Push;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.Journal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Read-only projection of the journal that collects completed tasks. A task is "completed" when it was pushed and later
 * popped. Tasks that were dropped (discarded during revise) are not included.
 */
public class ClewLog {

  private final Journal journal;
  private final JournalCodec<JournalRecord> codec;

  public ClewLog(Journal journal, JournalCodec<JournalRecord> codec) {
    this.journal = journal;
    this.codec = codec;
  }

  public List<String> list() {
    Deque<String> stack = new ArrayDeque<>();
    List<String> completed = new ArrayList<>();

    try (var stream = journal.openStream(0)) {
      stream.map(codec::decode).forEach(record -> {
        switch (record) {
          case Push(String msg) -> stack.addLast(msg);
          case Pop() -> {
            if (!stack.isEmpty()) {
              completed.add(stack.removeLast());
            }
          }
          case Drop() -> {
            if (!stack.isEmpty()) {
              stack.removeLast();
            }
          }
        }
      });
    }

    return List.copyOf(completed);
  }
}
