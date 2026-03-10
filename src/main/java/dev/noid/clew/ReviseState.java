package dev.noid.clew;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noid.clew.JournalEntry.Drop;
import dev.noid.clew.JournalEntry.Pop;
import dev.noid.clew.JournalEntry.Push;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public class ReviseState {

  public enum Slot {MAIN, A, B;}

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static ReviseState start(DiskJournal wal, Path scratchFile) {
    ArrayDeque<String> main = new ArrayDeque<>();
    if (Files.exists(scratchFile)) {
      throw new IllegalStateException("can't start new revise while active exists");
    }

    try {
      Files.createDirectories(scratchFile.getParent());
    } catch (IOException cause) {
      throw new UncheckedIOException(cause);
    }

    List<String> stack = new ArrayList<>();
    try(Stream<JournalEntry> entries = wal.openStream(0)) {
      for (JournalEntry entry : entries.toList()){
        if (entry instanceof Push(String msg)) {
          stack.add(msg);
        } else {
          stack.removeLast();
        }
      }
    } catch (Exception cause) {
      throw new IllegalStateException(cause);
    }
    for (int i = stack.size() - 1; i >= 0; i--) {
      main.push(stack.get(i));
    }

    ReviseState state = new ReviseState(wal, main, new ArrayDeque<>(), new ArrayDeque<>(),
        scratchFile);
    state.dump();
    return state;
  }

  public static ReviseState restore(DiskJournal wal, Path scratchFile) {
    ArrayDeque<String> main = new ArrayDeque<>();
    ArrayDeque<String> tempA = new ArrayDeque<>();
    ArrayDeque<String> tempB = new ArrayDeque<>();

    if (!Files.exists(scratchFile)) {
      throw new IllegalStateException("there is no active revise session");
    }

    try (InputStream source = Files.newInputStream(scratchFile)) {
      String[][] stacks = MAPPER.readValue(source, String[][].class);

      for (String task : stacks[0]) {
        main.push(task);
      }
      for (String task : stacks[1]) {
        tempA.push(task);
      }
      for (String task : stacks[2]) {
        tempB.push(task);
      }
    } catch (IOException cause) {
      throw new UncheckedIOException(cause);
    }
    return new ReviseState(wal, main, tempA, tempB, scratchFile);
  }

  private final DiskJournal wal;
  private final ArrayDeque<String> main;
  private final ArrayDeque<String> tempA;
  private final ArrayDeque<String> tempB;
  private final Path scratchFile;

  private ReviseState(
      DiskJournal wal,
      ArrayDeque<String> main,
      ArrayDeque<String> tempA,
      ArrayDeque<String> tempB,
      Path scratchFile) {
    this.wal = wal;
    this.main = main;
    this.tempA = tempA;
    this.tempB = tempB;
    this.scratchFile = scratchFile;
  }


  public void move(Slot from, Slot to) {
    if (from == to) {
      return;
    }

    ArrayDeque<String> source = switch (from) {
      case MAIN -> main;
      case A -> tempA;
      case B -> tempB;
    };

    ArrayDeque<String> target = switch (to) {
      case MAIN -> main;
      case A -> tempA;
      case B -> tempB;
    };

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
    main.pop(); // old message
    main.push(newMessage);
    dump();
  }

  // resolution
  public void commit() throws Exception {
    List<Pop> pops = new ArrayList<>();
    for (int i = 0; i < main.size(); i++) {
      pops.add(new Pop());
    }
    wal.append(pops);

    List<Push> pushes = new ArrayList<>();
    while (!main.isEmpty()) {
      pushes.add(new Push(main.removeLast()));
    }
    wal.append(pushes);

    List<Drop> drops = new ArrayList<>();
    for (int i = 0; i < tempA.size() + tempB.size(); i++) {
      drops.add(new Drop());
    }
    wal.append(drops);
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
    return main.stream().toList();
  }

  public List<String> tempA() {
    return tempA.stream().toList();
  }

  public List<String> tempB() {
    return tempB.stream().toList();
  }

  private void dump() {
    try {
      String[][] object = new String[3][];

      List<String> mainItems = new ArrayList<>(main);
      Collections.reverse(mainItems);
      object[0] = mainItems.toArray(String[]::new);

      List<String> tmpAItems = new ArrayList<>(tempA);
      Collections.reverse(tmpAItems);
      object[1] = tmpAItems.toArray(String[]::new);

      List<String> tmpBItems = new ArrayList<>(tempB);
      Collections.reverse(tmpBItems);
      object[2] = tmpBItems.toArray(String[]::new);

      String json = MAPPER.writeValueAsString(object);
      Files.writeString(scratchFile, json);
    } catch (IOException cause) {
      throw new UncheckedIOException(cause);
    }
  }
}