package dev.noid.clew.journal;

import dev.noid.clew.JournalRecord;
import dev.noid.clew.codec.JournalCodec;
import java.util.List;
import java.util.stream.Stream;

public record TypedJournal<T extends JournalRecord>(
    Journal raw,
    JournalCodec<T> codec
) implements AutoCloseable {

  @SafeVarargs
  public final long append(T... records) {
    List<byte[]> serialized = Stream.of(records)
        .map(codec::encode)
        .toList();

    return raw.append(serialized);
  }

  public Stream<T> openStream(long fromPosition) {
    return raw.openStream(fromPosition).map(codec::decode);
  }

  public long currentPosition() {
    return raw.currentPosition();
  }

  @Override
  public void close() {
    try {
      raw.close();
    } catch (Exception e) {
      throw new JournalException("Failed to close underlying journal", e);
    }
  }
}