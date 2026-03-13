package dev.noid.clew.journal.file;

import dev.noid.clew.journal.JournalCorruptionException;
import dev.noid.clew.journal.file.FileJournal;
import dev.noid.clew.journal.file.SegmentFrame;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class SegmentFrameTest {

  private Arena arena;
  private MemorySegment segment;

  @BeforeEach
  void setUp() {
    arena = Arena.ofConfined();
    segment = arena.allocate(1024);
  }

  @AfterEach
  void tearDown() {
    arena.close();
  }

  @Test
  @DisplayName("write then read back produces identical payload")
  void writeAndRead() {
    byte[] original = "Hello World".getBytes();
    long bytesWritten = SegmentFrame.write(segment, 0, original);

    SegmentFrame frame = new SegmentFrame(segment, 0);
    assertEquals(original.length + SegmentFrame.OVERHEAD, bytesWritten);
    assertTrue(frame.isPresent());
    assertTrue(frame.isValid());
    assertArrayEquals(original, frame.readPayload());
  }

  @Test
  @DisplayName("two sequential frames are independently readable")
  void sequentialFrames() {
    byte[] first = "alpha".getBytes();
    byte[] second = "beta".getBytes();

    long offset2 = SegmentFrame.write(segment, 0, first);
    SegmentFrame.write(segment, offset2, second);

    SegmentFrame frame1 = new SegmentFrame(segment, 0);
    assertTrue(frame1.isPresent());
    assertTrue(frame1.isValid());
    assertArrayEquals(first, frame1.readPayload());

    SegmentFrame frame2 = new SegmentFrame(segment, offset2);
    assertTrue(frame2.isPresent());
    assertTrue(frame2.isValid());
    assertArrayEquals(second, frame2.readPayload());
  }

  @Test
  @DisplayName("null payload throws IllegalArgumentException")
  void rejectsNullPayload() {
    assertThrows(IllegalArgumentException.class, () -> SegmentFrame.write(segment, 0, null));
  }

  @Test
  @DisplayName("empty payload round-trips correctly")
  void emptyPayloadRoundTrip() {
    long written = SegmentFrame.write(segment, 0, new byte[0]);

    SegmentFrame frame = new SegmentFrame(segment, 0);
    assertEquals(SegmentFrame.OVERHEAD, written);
    assertTrue(frame.isPresent());
    assertTrue(frame.isValid());
    assertArrayEquals(new byte[0], frame.readPayload());
  }

  @Test
  @DisplayName("flipped byte in payload is detected as invalid")
  void detectCorruption() {
    SegmentFrame.write(segment, 0, "Pure Data".getBytes());
    SegmentFrame frame = new SegmentFrame(segment, 0);
    assertTrue(frame.isValid());

    // offset 0-3: magic, 4-7: length, 8+: payload — target a payload byte
    segment.set(ValueLayout.JAVA_BYTE, 9, (byte) 'X');
    SegmentFrame corrupted = new SegmentFrame(segment, 0);
    assertFalse(corrupted.isValid());
  }

  @Test
  @DisplayName("corrupt length field in present frame throws corruption exception on validation")
  void corruptLengthInPresentFrame() {
    SegmentFrame.write(segment, 0, "data".getBytes());

    // corrupt the length field — magic stays intact at offset 0
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, Integer.BYTES, -1);

    SegmentFrame frame = new SegmentFrame(segment, 0);
    assertTrue(frame.isPresent());
    assertThrows(JournalCorruptionException.class, frame::isValid);
  }

  @Test
  @DisplayName("corrupted checksum field is detected as invalid")
  void corruptedChecksum_isDetected() {
    SegmentFrame.write(segment, 0, "payload".getBytes());
    SegmentFrame frame = new SegmentFrame(segment, 0);

    // corrupt the 4-byte checksum at the tail of the frame
    long checksumOffset = frame.totalSize() - Integer.BYTES;
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, checksumOffset, 0xDEADBEEF);

    SegmentFrame corrupted = new SegmentFrame(segment, 0);
    assertTrue(corrupted.isPresent());
    assertFalse(corrupted.isValid());
  }

  @Test
  @DisplayName("corrupted length to valid in-bounds value is detected")
  void corruptedLength_toValidRange_isDetected() {
    SegmentFrame.write(segment, 0, "Hello World".getBytes()); // 11 bytes
    SegmentFrame frame = new SegmentFrame(segment, 0);
    assertTrue(frame.isValid());

    // corrupt length field (at offset MAGIC_SIZE=4) from 11 to 5 — still in bounds
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, Integer.BYTES, 5);

    SegmentFrame corrupted = new SegmentFrame(segment, 0);
    assertTrue(corrupted.isPresent()); // magic intact
    assertFalse(corrupted.isValid());
  }

  @Test
  @DisplayName("uninitialized memory reports not present with zero totalSize")
  void uninitializedMemory() {
    SegmentFrame frame = new SegmentFrame(segment, 128);

    assertFalse(frame.isPresent());
    assertEquals(0, frame.totalSize());
    assertThrows(IllegalStateException.class, frame::readPayload);
  }

  @Test
  @DisplayName("offset at segment boundary reports not present with zero totalSize")
  void offsetAtBoundary() {
    MemorySegment small = arena.allocate(100);
    SegmentFrame frame = new SegmentFrame(small, 98);

    assertFalse(frame.isPresent());
    assertEquals(0, frame.totalSize());
    assertThrows(IllegalStateException.class, frame::readPayload);
  }
}
