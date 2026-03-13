package dev.noid.clew.journal.file;

import dev.noid.clew.journal.Journal;
import dev.noid.clew.journal.JournalException;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class FileJournal implements Journal {

  private final Arena arena;
  private final MemorySegment segment;
  private volatile long committedPosition;

  public FileJournal(Path filePath, long maxSize) {
    this.arena = Arena.ofShared();
    try {
      if (filePath.getParent() != null) {
        Files.createDirectories(filePath.getParent());
      }
      try (FileChannel channel = FileChannel.open(filePath,
          StandardOpenOption.CREATE,
          StandardOpenOption.READ,
          StandardOpenOption.WRITE)) {
        this.segment = channel.map(FileChannel.MapMode.READ_WRITE, 0, maxSize, arena);
      }
      this.committedPosition = recoverPosition();
    } catch (IOException cause) {
      arena.close();
      throw new JournalException("Failed to open journal at " + filePath, cause);
    }
  }

  @Override
  public synchronized long append(List<byte[]> records) {
    if (records == null || records.isEmpty()) {
      throw new IllegalArgumentException("Records must not be null or empty");
    }

    long totalSize = 0;
    for (byte[] payload : records) {
      if (payload == null) {
        throw new IllegalArgumentException("Record payload must not be null");
      }
      totalSize += (long) payload.length + SegmentFrame.OVERHEAD;
    }

    long writeHead = committedPosition;
    if (writeHead + totalSize > segment.byteSize()) {
      throw new IllegalStateException("Journal capacity exceeded");
    }

    for (byte[] payload : records) {
      writeHead += SegmentFrame.fill(segment, writeHead, payload).size() + SegmentFrame.OVERHEAD;
    }
    segment.asSlice(committedPosition, writeHead - committedPosition).force();
    committedPosition = writeHead;
    return writeHead;
  }

  @Override
  public Stream<byte[]> openStream(long fromPosition) {
    if (fromPosition < 0 || fromPosition > committedPosition) {
      throw new IllegalArgumentException("Invalid position: " + fromPosition);
    }

    if (fromPosition > 0 && fromPosition < committedPosition) {
      if (!new SegmentFrame(segment, fromPosition).isPresent()) {
        throw new IllegalArgumentException("Position does not align to a frame boundary: " + fromPosition);
      }
    }

    SegmentIterator iterator = new SegmentIterator(segment, fromPosition, committedPosition);
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator,
        Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE
    ), false);
  }

  @Override
  public long currentPosition() {
    return committedPosition;
  }

  @Override
  public synchronized void close() {
    arena.close();
  }

  private long recoverPosition() {
    long offset = 0;
    while (true) {
      SegmentFrame frame = new SegmentFrame(segment, offset);
      if (!frame.isPresent() || !frame.isValid()) {
        return offset;
      }
      offset += frame.size() + SegmentFrame.OVERHEAD;
    }
  }
}
