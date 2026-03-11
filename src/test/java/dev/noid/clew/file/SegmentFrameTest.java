package dev.noid.clew.file;

import dev.noid.clew.journal.JournalCorruptionException;
import dev.noid.clew.journal.file.SegmentFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

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
    SegmentFrame frame = new SegmentFrame(segment, 0);
    byte[] original = "Hello World".getBytes();
    long bytesWritten = frame.write(original);

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

    SegmentFrame frame1 = new SegmentFrame(segment, 0);
    long offset2 = frame1.write(first);

    SegmentFrame frame2 = new SegmentFrame(segment, offset2);
    frame2.write(second);

    assertTrue(frame1.isPresent());
    assertTrue(frame1.isValid());
    assertArrayEquals(first, frame1.readPayload());

    assertTrue(frame2.isPresent());
    assertTrue(frame2.isValid());
    assertArrayEquals(second, frame2.readPayload());
  }

  @Test
  @DisplayName("flipped byte in payload is detected as invalid")
  void detectCorruption() {
    SegmentFrame frame = new SegmentFrame(segment, 0);
    frame.write("Pure Data".getBytes());
    assertTrue(frame.isValid());

    segment.set(ValueLayout.JAVA_BYTE, 5, (byte) 'X');
    assertFalse(frame.isValid());
  }

  @Test
  @DisplayName("negative length field throws corruption exception")
  void negativeLengthIsCorruption() {
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, 0, -1);
    SegmentFrame frame = new SegmentFrame(segment, 0);
    assertThrows(JournalCorruptionException.class, frame::isPresent);
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
