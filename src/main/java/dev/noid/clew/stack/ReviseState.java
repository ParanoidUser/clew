package dev.noid.clew.stack;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noid.clew.TaskEvent;
import dev.noid.clew.TaskEvent.*;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.journal.Journal;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public class ReviseState {

  public enum Slot {MAIN, A, B;}

  record TaskRef(String taskId, String description) {}

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static ReviseState start(Journal journal, JournalCodec<TaskEvent> codec, Path scratchFile) {
    if (Files.exists(scratchFile)) {
      throw new IllegalStateException("can't start new revise while active exists");
    }

    try {
      Files.createDirectories(scratchFile.getParent());
    } catch (IOException cause) {
      throw new UncheckedIOException(cause);
    }

    ArrayDeque<TaskRef> main = new ArrayDeque<>();
    try (var stream = journal.openStream(0)) {
      for (TaskEvent event : stream.map(codec::decode).toList()) {
        switch (event) {
          case TaskCreated e -> main.addFirst(new TaskRef(e.taskId(), e.description()));
          case TaskCompleted e -> removeById(main, e.taskId());
          case TaskDropped e -> removeById(main, e.taskId());
          case TaskContentUpdated e -> updateDescription(main, e.taskId(), e.description());
          case TaskActivated ignored -> {}
          case TaskDeactivated ignored -> {}
        }
      }
    }

    List<TaskRef> originalOrder = List.copyOf(main);
    ReviseState state = new ReviseState(
        journal, codec, main, new ArrayDeque<>(), new ArrayDeque<>(), originalOrder, scratchFile);
    state.dump();
    return state;
  }

  public static ReviseState restore(Journal journal, JournalCodec<TaskEvent> codec, Path scratchFile) {
    if (!Files.exists(scratchFile)) {
      throw new IllegalStateException("there is no active revise session");
    }

    ArrayDeque<TaskRef> main = new ArrayDeque<>();
    ArrayDeque<TaskRef> tempA = new ArrayDeque<>();
    ArrayDeque<TaskRef> tempB = new ArrayDeque<>();

    try (InputStream source = Files.newInputStream(scratchFile)) {
      TaskRef[][] stacks = MAPPER.readValue(source, TaskRef[][].class);
      for (TaskRef ref : stacks[0]) {
        main.addLast(ref);
      }
      for (TaskRef ref : stacks[1]) {
        tempA.addLast(ref);
      }
      for (TaskRef ref : stacks[2]) {
        tempB.addLast(ref);
      }
    } catch (IOException cause) {
      throw new UncheckedIOException(cause);
    }

    // Rebuild original order from journal
    ArrayDeque<TaskRef> originalFromJournal = new ArrayDeque<>();
    try (var stream = journal.openStream(0)) {
      for (TaskEvent event : stream.map(codec::decode).toList()) {
        switch (event) {
          case TaskCreated e ->
              originalFromJournal.addFirst(new TaskRef(e.taskId(), e.description()));
          case TaskCompleted e -> removeById(originalFromJournal, e.taskId());
          case TaskDropped e -> removeById(originalFromJournal, e.taskId());
          case TaskContentUpdated e ->
              updateDescription(originalFromJournal, e.taskId(), e.description());
          case TaskActivated ignored -> {}
          case TaskDeactivated ignored -> {}
        }
      }
    }

    return new ReviseState(
        journal, codec, main, tempA, tempB, List.copyOf(originalFromJournal), scratchFile);
  }

  private final Journal journal;
  private final JournalCodec<TaskEvent> codec;
  private final ArrayDeque<TaskRef> main;
  private final ArrayDeque<TaskRef> tempA;
  private final ArrayDeque<TaskRef> tempB;
  private final List<TaskRef> originalOrder; // front = top, immutable snapshot from start()
  private final Path scratchFile;

  private ReviseState(
      Journal journal,
      JournalCodec<TaskEvent> codec,
      ArrayDeque<TaskRef> main,
      ArrayDeque<TaskRef> tempA,
      ArrayDeque<TaskRef> tempB,
      List<TaskRef> originalOrder,
      Path scratchFile) {
    this.journal = journal;
    this.codec = codec;
    this.main = main;
    this.tempA = tempA;
    this.tempB = tempB;
    this.originalOrder = originalOrder;
    this.scratchFile = scratchFile;
  }

  public void move(Slot from, Slot to) {
    if (from == to) {
      return;
    }
    ArrayDeque<TaskRef> source = dequeFor(from);
    ArrayDeque<TaskRef> target = dequeFor(to);
    if (source.isEmpty()) {
      throw new NoSuchElementException("source stack is empty");
    }
    target.push(source.pop());
    dump();
  }

  public void edit(String newMessage) {
    if (main.isEmpty()) {
      throw new NoSuchElementException("main stack is empty");
    }
    TaskRef old = main.pop();
    main.push(new TaskRef(old.taskId(), newMessage));
    dump();
  }

  public void commit() {
    long ts = System.currentTimeMillis();
    List<byte[]> batch = new ArrayList<>();

    String oldTopId = originalOrder.isEmpty() ? null : originalOrder.getFirst().taskId();
    String newTopId = main.isEmpty() ? null : main.peekFirst().taskId();
    boolean topChanged = !Objects.equals(oldTopId, newTopId);

    // Dropped tasks (in tempA/tempB)
    List<TaskRef> discarded = new ArrayList<>();
    discarded.addAll(tempA);
    discarded.addAll(tempB);

    // Determine if kept task order changed
    List<String> keptIds = main.stream().map(TaskRef::taskId).toList();
    List<String> originalKeptIds = originalOrder.stream()
        .map(TaskRef::taskId)
        .filter(id -> keptIds.contains(id))
        .toList();
    boolean orderChanged = !originalKeptIds.equals(keptIds);

    if (orderChanged && !main.isEmpty()) {
      // Interim approach: drop all tasks, recreate kept tasks in new order.
      // This resets creation timestamps — acceptable until position events are added.
      if (oldTopId != null) {
        batch.add(codec.encode(new TaskDeactivated(ts, oldTopId)));
      }
      for (TaskRef ref : discarded) {
        batch.add(codec.encode(new TaskDropped(ts, ref.taskId())));
      }
      for (TaskRef ref : originalOrder) {
        if (keptIds.contains(ref.taskId())) {
          batch.add(codec.encode(new TaskDropped(ts, ref.taskId())));
        }
      }
      var it = main.descendingIterator(); // bottom first
      while (it.hasNext()) {
        TaskRef ref = it.next();
        batch.add(codec.encode(new TaskCreated(ts, ref.taskId(), ref.description())));
      }
      if (newTopId != null) {
        batch.add(codec.encode(new TaskActivated(ts, newTopId)));
      }
    } else {
      // No order change — emit targeted events only
      if (topChanged && oldTopId != null) {
        batch.add(codec.encode(new TaskDeactivated(ts, oldTopId)));
      }
      for (TaskRef ref : discarded) {
        batch.add(codec.encode(new TaskDropped(ts, ref.taskId())));
      }
      for (TaskRef ref : main) {
        TaskRef original = findOriginal(ref.taskId());
        if (original != null && !original.description().equals(ref.description())) {
          batch.add(codec.encode(new TaskContentUpdated(ts, ref.taskId(), ref.description())));
        }
      }
      if (topChanged && newTopId != null) {
        batch.add(codec.encode(new TaskActivated(ts, newTopId)));
      }
    }

    if (!batch.isEmpty()) {
      journal.append(batch);
    }

    try {
      Files.delete(scratchFile);
    } catch (IOException cause) {
      throw new UncheckedIOException(cause);
    }
  }

  public void cancel() {
    try {
      Files.delete(scratchFile);
    } catch (IOException cause) {
      throw new UncheckedIOException(cause);
    }
  }

  public List<String> main() {
    return main.stream().map(TaskRef::description).toList();
  }

  public List<String> tempA() {
    return tempA.stream().map(TaskRef::description).toList();
  }

  public List<String> tempB() {
    return tempB.stream().map(TaskRef::description).toList();
  }

  private ArrayDeque<TaskRef> dequeFor(Slot slot) {
    return switch (slot) {
      case MAIN -> main;
      case A -> tempA;
      case B -> tempB;
    };
  }

  private TaskRef findOriginal(String taskId) {
    return originalOrder.stream()
        .filter(r -> r.taskId().equals(taskId))
        .findFirst()
        .orElse(null);
  }

  private void dump() {
    try {
      TaskRef[][] data = new TaskRef[3][];
      data[0] = main.toArray(TaskRef[]::new);
      data[1] = tempA.toArray(TaskRef[]::new);
      data[2] = tempB.toArray(TaskRef[]::new);
      Files.writeString(scratchFile, MAPPER.writeValueAsString(data));
    } catch (IOException cause) {
      throw new UncheckedIOException(cause);
    }
  }

  private static void removeById(ArrayDeque<TaskRef> deque, String taskId) {
    deque.removeIf(ref -> ref.taskId().equals(taskId));
  }

  private static void updateDescription(ArrayDeque<TaskRef> deque, String taskId, String desc) {
    ArrayDeque<TaskRef> updated = new ArrayDeque<>(deque.size());
    for (TaskRef ref : deque) {
      if (ref.taskId().equals(taskId)) {
        updated.addLast(new TaskRef(taskId, desc));
      } else {
        updated.addLast(ref);
      }
    }
    deque.clear();
    deque.addAll(updated);
  }
}
