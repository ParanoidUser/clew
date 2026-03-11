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
import java.util.stream.Stream;

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
    } catch (IOException e) {
      arena.close();
      throw new JournalException("Failed to open journal at " + filePath, e);
    }
  }

  @Override
  public synchronized long append(List<byte[]> records) {
    if (records == null || records.isEmpty()) {
      throw new IllegalArgumentException("Records must not be null or empty");
    }

    long writeHead = committedPosition;
    for (byte[] payload : records) {
      SegmentFrame frame = new SegmentFrame(segment, writeHead);
      writeHead += frame.write(payload);
    }
    segment.force();
    committedPosition = writeHead;
    return committedPosition;
  }

  @Override
  public Stream<byte[]> openStream(long fromPosition) {
    if (fromPosition < 0) {
      throw new IllegalArgumentException("Position must not be negative: %d".formatted(fromPosition));
    }
    return new SegmentScanner(segment, fromPosition, committedPosition).stream();
  }

  @Override
  public long currentPosition() {
    return committedPosition;
  }

  @Override
  public void close() {
    arena.close();
  }

  private long recoverPosition() {
    long offset = 0;
    while (true) {
      SegmentFrame frame = new SegmentFrame(segment, offset);
      if (!frame.isPresent() || !frame.isValid()) {
        return offset;
      }
      offset += frame.totalSize();
    }
  }
}
