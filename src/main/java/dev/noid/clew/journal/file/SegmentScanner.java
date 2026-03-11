package dev.noid.clew.journal.file;

import dev.noid.clew.journal.JournalCorruptionException;
import java.lang.foreign.MemorySegment;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class SegmentScanner implements Iterator<byte[]> {

  private final MemorySegment segment;
  private long currentOffset;
  private final long limit;

  public SegmentScanner(MemorySegment segment, long startOffset, long limit) {
    this.segment = segment;
    this.currentOffset = startOffset;
    this.limit = limit;
  }

  @Override
  public boolean hasNext() {
    if (currentOffset >= limit) {
      return false;
    }
    SegmentFrame candidate = new SegmentFrame(segment, currentOffset);
    return candidate.isPresent();
  }

  @Override
  public byte[] next() {
    SegmentFrame candidate = new SegmentFrame(segment, currentOffset);
    if (!candidate.isPresent()) {
      throw new NoSuchElementException("End of journal reached");
    }

    if (!candidate.isValid()) {
      throw new JournalCorruptionException("Corruption detected at offset " + currentOffset);
    }

    byte[] payload = candidate.readPayload();
    currentOffset += candidate.totalSize();
    return payload;
  }

  public Stream<byte[]> stream() {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(this, Spliterator.ORDERED | Spliterator.NONNULL),
        false
    );
  }
}