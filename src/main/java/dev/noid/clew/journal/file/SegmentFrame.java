package dev.noid.clew.journal.file;

import dev.noid.clew.journal.JournalCorruptionException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.zip.CRC32C;

/**
 * A single record frame in the journal's binary format.
 * <p>
 * Layout: {@code [magic:4][length:4][payload:N][checksum:4]}
 * <p>
 * A frame is <b>present</b> if the magic constant {@code "clew"} is found at the frame offset.
 * A frame is <b>valid</b> if it is present, the length field is in bounds, and the stored checksum
 * matches the computed checksum over the magic, length, and payload fields.
 */
public final class SegmentFrame {

  private static final int FRAME_MAGIC = 0x636C6577; // "clew"
  private static final int MAGIC_SIZE = 4;
  private static final int LENGTH_SIZE = 4;
  private static final int CHECKSUM_SIZE = 4;
  public static final int OVERHEAD = MAGIC_SIZE + LENGTH_SIZE + CHECKSUM_SIZE;

  private final MemorySegment segment;
  private final long offset;
  private int cachedLength = -1;

  SegmentFrame(MemorySegment segment, long offset) {
    this.segment = segment;
    this.offset = offset;
  }

  public boolean isPresent() {
    if (offset + MAGIC_SIZE > segment.byteSize()) {
      return false;
    }
    return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset) == FRAME_MAGIC;
  }

  public boolean isValid() {
    if (!isPresent()) {
      return false;
    }
    int len = rawLength();
    if (len < 0 || offset + OVERHEAD + len > segment.byteSize()) {
      throw new JournalCorruptionException(
          "Invalid frame length at offset " + offset + ": " + len);
    }
    int stored = segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + MAGIC_SIZE + LENGTH_SIZE + len);
    int computed = checksum(segment, offset, MAGIC_SIZE + LENGTH_SIZE + len);
    return stored == computed;
  }

  public long totalSize() {
    if (!isPresent()) {
      return 0;
    }
    return (long) rawLength() + OVERHEAD;
  }

  public byte[] readPayload() {
    if (!isPresent()) {
      throw new IllegalStateException("No data present at offset " + offset);
    }
    int len = rawLength();
    byte[] data = new byte[len];
    MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset + MAGIC_SIZE + LENGTH_SIZE, data, 0, len);
    return data;
  }

  static long write(MemorySegment segment, long offset, byte[] payload) {
    if (payload == null) {
      throw new IllegalArgumentException("Record payload must not be null");
    }

    int len = payload.length;
    MemorySegment source = MemorySegment.ofArray(payload);

    segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset, FRAME_MAGIC);
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + MAGIC_SIZE, len);
    MemorySegment.copy(source, 0, segment, offset + MAGIC_SIZE + LENGTH_SIZE, len);
    int crc = checksum(segment, offset, MAGIC_SIZE + LENGTH_SIZE + len);
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + MAGIC_SIZE + LENGTH_SIZE + len, crc);

    return (long) len + OVERHEAD;
  }

  private int rawLength() {
    if (cachedLength == -1) {
      cachedLength = segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + MAGIC_SIZE);
    }
    return cachedLength;
  }

  private static int checksum(MemorySegment source, long off, int len) {
    CRC32C crc = new CRC32C();
    crc.update(source.asSlice(off, len).asByteBuffer());
    return (int) crc.getValue();
  }
}
