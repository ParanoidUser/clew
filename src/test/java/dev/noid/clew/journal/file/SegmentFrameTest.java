package dev.noid.clew.journal.file;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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
    SegmentFrame frame = SegmentFrame.fill(segment, 0, original);

    assertTrue(frame.isPresent());
    assertTrue(frame.isValid());
    assertEquals(original.length, frame.size());
    assertArrayEquals(original, frame.payload());
  }

  @Test
  @DisplayName("two sequential frames are independently readable")
  void sequentialFrames() {
    byte[] first = "alpha".getBytes();
    SegmentFrame frame1 = SegmentFrame.fill(segment, 0, first);

    assertTrue(frame1.isPresent());
    assertTrue(frame1.isValid());
    assertEquals(first.length, frame1.size());
    assertArrayEquals(first, frame1.payload());

    byte[] second = "beta".getBytes();
    SegmentFrame frame2 = SegmentFrame.fill(segment, frame1.size() + SegmentFrame.OVERHEAD, second);

    assertTrue(frame2.isPresent());
    assertTrue(frame2.isValid());
    assertEquals(second.length, frame2.size());
    assertArrayEquals(second, frame2.payload());
  }

  @Test
  @DisplayName("null payload throws IllegalArgumentException")
  void rejectsNullPayload() {
    assertThrows(IllegalArgumentException.class, () -> SegmentFrame.fill(segment, 0, null));
  }

  @Test
  @DisplayName("empty payload round-trips correctly")
  void emptyPayloadRoundTrip() {
    SegmentFrame empty = SegmentFrame.fill(segment, 0, new byte[0]);

    assertTrue(empty.isPresent());
    assertTrue(empty.isValid());
    assertEquals(0, empty.size());
    assertArrayEquals(new byte[0], empty.payload());
  }

  @Test
  @DisplayName("corrupted magic field is detected as not present and invalid")
  void corruptedMagic() {
    byte[] original = "data".getBytes();
    SegmentFrame frame = SegmentFrame.fill(segment, 0, original);
    assertTrue(frame.isValid());

    // Offset: [magic:0-3][length:4-7][payload:8-11][checksum:12-15]
    segment.set(ValueLayout.JAVA_BYTE, 0, (byte) 'b');

    assertFalse(frame.isPresent());
    assertFalse(frame.isValid());
    assertEquals(original.length, frame.size());
    assertArrayEquals(original, frame.payload());
  }

  @Test
  @DisplayName("corrupted length field to valid in-bounds value is detected as invalid")
  void corruptedLength_toValidRange() {
    byte[] original = "data".getBytes();
    SegmentFrame frame = SegmentFrame.fill(segment, 0, original);
    assertTrue(frame.isValid());

    // Offset: [magic:0-3][length:4-7][payload:8-11][checksum:12-15]
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, 4, original.length - 1);

    assertTrue(frame.isPresent());
    assertFalse(frame.isValid());
    assertEquals(original.length - 1, frame.size());
    assertArrayEquals("dat".getBytes(), frame.payload());
  }

  @Test
  @DisplayName("corrupted length field out of valid bounds is detected as invalid")
  void corruptedLength_outOfRange() {
    byte[] original = "data".getBytes();
    SegmentFrame frame = SegmentFrame.fill(segment, 0, original);
    assertTrue(frame.isValid());

    // Offset: [magic:0-3][length:4-7][payload:8-11][checksum:12-15]
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, 4, 12);

    assertTrue(frame.isPresent());
    assertFalse(frame.isValid());
    assertEquals(12, frame.size());
    assertArrayEquals(new byte[]{'d', 'a', 't', 'a', -89, -6, -18, 88, 0, 0, 0, 0}, frame.payload()); // OS-dependent?
  }

  @Test
  @DisplayName("corrupted length field to negative value is detected as invalid")
  void corruptedLength_toNegativeRange() {
    byte[] original = "data".getBytes();
    SegmentFrame frame = SegmentFrame.fill(segment, 0, original);
    assertTrue(frame.isValid());

    // Offset: [magic:0-3][length:4-7][payload:8-11][checksum:12-15]
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, 4, -42);

    assertTrue(frame.isPresent());
    assertFalse(frame.isValid());
    assertEquals(-42, frame.size());
    assertNull(frame.payload());
  }

  @Test
  @DisplayName("corrupted payload is detected as invalid")
  void corruptedPayload() {
    byte[] original = "data".getBytes();
    SegmentFrame frame = SegmentFrame.fill(segment, 0, original);
    assertTrue(frame.isValid());

    // Offset: [magic:0-3][length:4-7][payload:8-11][checksum:12-15]
    segment.set(ValueLayout.JAVA_BYTE, 9, (byte) '_');

    assertTrue(frame.isPresent());
    assertFalse(frame.isValid());
    assertEquals(original.length, frame.size());
    assertArrayEquals("d_ta".getBytes(), frame.payload());
  }

  @Test
  @DisplayName("corrupted checksum field is detected as invalid")
  void corruptedChecksum() {
    byte[] original = "data".getBytes();
    SegmentFrame frame = SegmentFrame.fill(segment, 0, original);
    assertTrue(frame.isValid());

    // Offset: [magic:0-3][length:4-7][payload:8-11][checksum:12-15]
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, 12, 0xDEADBEEF);

    assertTrue(frame.isPresent());
    assertFalse(frame.isValid());
    assertEquals(original.length, frame.size());
    assertArrayEquals(original, frame.payload());
  }

  @Test
  @DisplayName("uninitialized memory detected as not present and invalid")
  void uninitializedMemory() {
    SegmentFrame frame = new SegmentFrame(segment, 128);

    assertFalse(frame.isPresent());
    assertFalse(frame.isValid());
    assertEquals(0, frame.size());
    assertArrayEquals(new byte[0], frame.payload());
  }

  @Test
  @DisplayName("offset at segment boundary reports not present")
  void offsetAtBoundary() {
    MemorySegment small = arena.allocate(100);
    SegmentFrame frame = new SegmentFrame(small, 98);

    assertFalse(frame.isPresent());
    assertFalse(frame.isValid());
    assertEquals(-1, frame.size());
    assertNull(frame.payload());
  }
}
