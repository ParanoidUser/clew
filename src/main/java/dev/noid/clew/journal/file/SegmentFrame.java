package dev.noid.clew.journal.file;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.zip.CRC32C;

/**
 * A single record frame in the journal's binary format.
 * <p>
 * Layout: {@code [magic:4][length:4][payload:N][checksum:4]}
 * <p>
 * A frame is <b>present</b> if the magic constant {@code "clew"} is found at the frame offset. A frame is <b>valid</b>
 * if it is present, the length field is in bounds, and the stored checksum matches the computed checksum over the
 * magic, length, and payload fields.
 */
public final class SegmentFrame {

  private static final int FRAME_MAGIC = 0x636C6577; // "clew"
  private static final int MAGIC_SIZE = 4;
  private static final int LENGTH_SIZE = 4;
  private static final int CHECKSUM_SIZE = 4;
  public static final int OVERHEAD = MAGIC_SIZE + LENGTH_SIZE + CHECKSUM_SIZE;

  static SegmentFrame fill(MemorySegment segment, long offset, byte[] payload) {
    if (payload == null) {
      throw new IllegalArgumentException("Record payload must not be null");
    }

    segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset, FRAME_MAGIC);
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + MAGIC_SIZE, payload.length);

    MemorySegment source = MemorySegment.ofArray(payload);
    MemorySegment.copy(source, 0, segment, offset + MAGIC_SIZE + LENGTH_SIZE, payload.length);

    int checksum = checksum(segment, offset, MAGIC_SIZE + LENGTH_SIZE + payload.length);
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + MAGIC_SIZE + LENGTH_SIZE + payload.length, checksum);

    return new SegmentFrame(segment, offset);
  }

  private final MemorySegment segment;
  private final long offset;

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
    int length = size();
    if (length < 0 || offset + OVERHEAD + length > segment.byteSize()) {
      return false;
    }

    int storedChecksum = segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + MAGIC_SIZE + LENGTH_SIZE + length);
    int actualChecksum = checksum(segment, offset, MAGIC_SIZE + LENGTH_SIZE + length);
    return storedChecksum == actualChecksum;
  }

  public byte[] payload() {
    int length = size();
    if (length < 0 || offset + OVERHEAD + length > segment.byteSize()) {
      return null;
    }

    byte[] payload = new byte[length];
    MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset + MAGIC_SIZE + LENGTH_SIZE, payload, 0, length);
    return payload;
  }

  public int size() {
    if (offset + MAGIC_SIZE + LENGTH_SIZE > segment.byteSize()) {
      return -1;
    }
    return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + MAGIC_SIZE);
  }

  private static int checksum(MemorySegment source, long off, int len) {
    CRC32C crc = new CRC32C();
    crc.update(source.asSlice(off, len).asByteBuffer());
    return (int) crc.getValue();
  }
}
