package dev.noid.clew.journal.file;

import dev.noid.clew.journal.JournalCorruptionException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.zip.CRC32C;

/**
 * A single record frame in the journal's binary format.
 * <p>
 * Layout: {@code [length:4][payload:N][checksum:4]}
 * <p>
 * A frame is <b>present</b> if there is enough space for the header and the length field is non-zero.
 * A frame is <b>valid</b> if it is present and the stored checksum matches the computed checksum.
 */
public final class SegmentFrame {

  private static final int LENGTH_SIZE = 4;
  private static final int CHECKSUM_SIZE = 4;
  public static final int OVERHEAD = LENGTH_SIZE + CHECKSUM_SIZE;

  private final MemorySegment segment;
  private final long offset;

  public SegmentFrame(MemorySegment segment, long offset) {
    this.segment = segment;
    this.offset = offset;
  }

  public boolean isPresent() {
    if (offset + LENGTH_SIZE > segment.byteSize()) {
      return false;
    }
    int len = rawLength();
    if (len == 0) {
      return false;
    }
    if (len < 0 || offset + len + OVERHEAD > segment.byteSize()) {
      throw new JournalCorruptionException("Invalid frame at offset " + offset);
    }
    return true;
  }

  public boolean isValid() {
    if (!isPresent()) {
      return false;
    }
    int len = rawLength();
    int stored = segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + LENGTH_SIZE + len);
    int computed = checksum(segment, offset + LENGTH_SIZE, len);
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
    MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset + LENGTH_SIZE, data, 0, len);
    return data;
  }

  public long write(byte[] payload) {
    int len = payload.length;
    MemorySegment source = MemorySegment.ofArray(payload);
    int crc = checksum(source, 0, len);

    segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset, len);
    MemorySegment.copy(source, 0, segment, offset + LENGTH_SIZE, len);
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + LENGTH_SIZE + len, crc);

    return (long) len + OVERHEAD;
  }

  private int rawLength() {
    return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset);
  }

  private static int checksum(MemorySegment source, long off, int len) {
    CRC32C crc = new CRC32C();
    crc.update(source.asSlice(off, len).asByteBuffer());
    return (int) crc.getValue();
  }
}
