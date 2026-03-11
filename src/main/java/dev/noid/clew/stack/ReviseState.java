package dev.noid.clew.stack;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noid.clew.JournalRecord;
import dev.noid.clew.codec.JournalCodec;
import dev.noid.clew.JournalRecord.Pop;
import dev.noid.clew.JournalRecord.Push;
import dev.noid.clew.journal.Journal;
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

  public static ReviseState start(Journal journal, JournalCodec<JournalRecord> codec, Path scratchFile) {
    if (Files.exists(scratchFile)) {
      throw new IllegalStateException("can't start new revise while active exists");
    }

    try {
      Files.createDirectories(scratchFile.getParent());
    } catch (IOException cause) {
      throw new UncheckedIOException(cause);
    }

    ArrayDeque<String> main = new ArrayDeque<>();
    try (Stream<byte[]> records = journal.openStream(0)) {
      for (JournalRecord entry : records.map(codec::decode).toList()) {
        if (entry instanceof Push(String msg)) {
          main.addFirst(msg);
        } else {
          main.removeFirst();
        }
      }
    } catch (Exception cause) {
      throw new IllegalStateException(cause);
    }

    ReviseState state = new ReviseState(journal, codec, main, new ArrayDeque<>(), new ArrayDeque<>(), scratchFile);
    state.dump();
    return state;
  }

  public static ReviseState restore(Journal journal, JournalCodec<JournalRecord> codec, Path scratchFile) {
    if (!Files.exists(scratchFile)) {
      throw new IllegalStateException("there is no active revise session");
    }

    ArrayDeque<String> main = new ArrayDeque<>();
    ArrayDeque<String> tempA = new ArrayDeque<>();
    ArrayDeque<String> tempB = new ArrayDeque<>();

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
    return new ReviseState(journal, codec, main, tempA, tempB, scratchFile);
  }

  private final Journal journal;
  private final JournalCodec<JournalRecord> codec;
  private final ArrayDeque<String> main;
  private final ArrayDeque<String> tempA;
  private final ArrayDeque<String> tempB;
  private final Path scratchFile;

  private ReviseState(
      Journal journal,
      JournalCodec<JournalRecord> codec,
      ArrayDeque<String> main,
      ArrayDeque<String> tempA,
      ArrayDeque<String> tempB,
      Path scratchFile) {
    this.journal = journal;
    this.codec = codec;
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
    main.pop();
    main.push(newMessage);
    dump();
  }

  public void commit() {
    int originalSize = main.size() + tempA.size() + tempB.size();

    int dropCount = tempA.size() + tempB.size();

    List<byte[]> batch = new ArrayList<>();
    for (int i = 0; i < main.size(); i++) {
      batch.add(codec.encode(new Pop()));
    }
    for (int i = 0; i < dropCount; i++) {
      batch.add(codec.encode(new JournalRecord.Drop()));
    }
    while (!main.isEmpty()) {
      batch.add(codec.encode(new Push(main.removeLast())));
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
