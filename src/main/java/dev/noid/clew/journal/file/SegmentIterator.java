package dev.noid.clew.journal.file;

import dev.noid.clew.journal.JournalCorruptionException;
import java.lang.foreign.MemorySegment;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class SegmentIterator implements Iterator<byte[]> {

  private final MemorySegment segment;
  private long currentOffset;
  private final long limit;
  private SegmentFrame nextFrame;

  public SegmentIterator(MemorySegment segment, long startOffset, long limit) {
    this.segment = segment;
    this.currentOffset = startOffset;
    this.limit = limit;
  }

  @Override
  public boolean hasNext() {
    if (nextFrame != null) {
      return true;
    }
    if (currentOffset >= limit) {
      return false;
    }
    SegmentFrame candidate = new SegmentFrame(segment, currentOffset);
    if (!candidate.isPresent()) {
      throw new JournalCorruptionException(
          "Missing frame within committed region at offset " + currentOffset);
    }
    if (!candidate.isValid()) {
      throw new JournalCorruptionException(
          "Corruption detected at offset " + currentOffset);
    }
    nextFrame = candidate;
    return true;
  }

  @Override
  public byte[] next() {
    if (!hasNext()) {
      throw new NoSuchElementException("End of journal reached");
    }
    byte[] payload = nextFrame.readPayload();
    currentOffset += nextFrame.totalSize();
    nextFrame = null;
    return payload;
  }

}