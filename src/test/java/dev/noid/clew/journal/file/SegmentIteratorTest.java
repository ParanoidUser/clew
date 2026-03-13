package dev.noid.clew.journal.file;

import dev.noid.clew.journal.JournalCorruptionException;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SegmentIteratorTest {

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
  @DisplayName("empty segment produces empty stream")
  void emptySegment() {
    SegmentIterator iterator = new SegmentIterator(segment, 0, 0);
    assertFalse(iterator.hasNext());
  }

  @Test
  @DisplayName("three frames are yielded in order")
  void sequentialScan() {
    long end = 0;
    end = writeFrame(end, "first");
    end = writeFrame(end, "second");
    end = writeFrame(end, "third");

    SegmentIterator iterator = new SegmentIterator(segment, 0, end);
    assertEquals(List.of("first", "second", "third"), readAllFrames(iterator));
  }

  @Test
  @DisplayName("starting from second frame offset skips the first")
  void startFromMiddle() {
    long second = writeFrame(0, "first");
    long end = writeFrame(second, "second");

    SegmentIterator iterator = new SegmentIterator(segment, second, end);
    assertEquals(List.of("second"), readAllFrames(iterator));
  }

  @Test
  @DisplayName("starting at limit (caught up) produces empty stream")
  void startAtLimit() {
    long end = writeFrame(0, "data");

    SegmentIterator iterator = new SegmentIterator(segment, end, end);
    assertFalse(iterator.hasNext());
  }

  @Test
  @DisplayName("hasNext returning true guarantees next() does not throw")
  void hasNextTrueGuaranteesNextSucceeds() {
    long end = writeFrame(0, "a");
    end = writeFrame(end, "b");
    end = writeFrame(end, "c");

    SegmentIterator iterator = new SegmentIterator(segment, 0, end);
    while (iterator.hasNext()) {
      // contract: if hasNext() returned true, next() must return a value without throwing
      assertDoesNotThrow(iterator::next);
    }
  }

  @Test
  @DisplayName("corruption is detected in hasNext() before it returns true")
  void corruptFrame_detectedInHasNext_notInNext() {
    long second = writeFrame(0, "good");
    long end = writeFrame(second, "corrupt");

    // Offset: [magic:0-3][length:4-7][payload:8-11][checksum:12-15]
    // flip a byte in the second frame's payload
    segment.set(ValueLayout.JAVA_BYTE, (int) second + 9, (byte) 0xFF);

    SegmentIterator iterator = new SegmentIterator(segment, 0, end);
    assertArrayEquals("good".getBytes(), iterator.next());
    assertThrows(JournalCorruptionException.class, iterator::hasNext);
    assertThrows(JournalCorruptionException.class, iterator::next);
  }

  @Test
  @DisplayName("corrupt frame mid-stream throws JournalCorruptionException")
  void corruptionMidStream() {
    long second = writeFrame(0, "good");
    long third = writeFrame(second, "will-corrupt");
    long end = writeFrame(third, "unreachable");

    // Offset: [magic:0-3][length:4-7][payload:8-11][checksum:12-15]
    // flip a byte in the second frame's payload
    segment.set(ValueLayout.JAVA_BYTE, second + 5, (byte) 0xFF);

    SegmentIterator iterator = new SegmentIterator(segment, 0, end);
    assertArrayEquals("good".getBytes(), iterator.next());
    assertThrows(JournalCorruptionException.class, iterator::next);
    assertThrows(JournalCorruptionException.class, iterator::next);
  }

  @Test
  @DisplayName("corrupted magic on middle frame within committed region throws JournalCorruptionException")
  void corruptedMagic_middleFrame_throwsCorruption() {
    long second = writeFrame(0, "good");
    long third = writeFrame(second, "will-corrupt");
    long end = writeFrame(third, "unreachable");

    // Offset: [magic:0-3][length:4-7][payload:8-11][checksum:12-15]
    // flip bytes to zero in the second frame's magic
    segment.set(ValueLayout.JAVA_INT_UNALIGNED, (int) second, 0x00000000);

    SegmentIterator iterator = new SegmentIterator(segment, 0, end);
    assertArrayEquals("good".getBytes(), iterator.next());
    assertThrows(JournalCorruptionException.class, iterator::next);
  }

  @Test
  @DisplayName("frames beyond limit are not visible (read isolation)")
  void readIsolation() {
    long second = writeFrame(0, "visible");
    writeFrame(second, "invisible");

    SegmentIterator iterator = new SegmentIterator(segment, 0, second);
    assertEquals(List.of("visible"), readAllFrames(iterator));
  }

  private long writeFrame(long offset, String payload) {
    int length = SegmentFrame.fill(segment, offset, payload.getBytes()).size();
    return offset + length + SegmentFrame.OVERHEAD;
  }

  private List<String> readAllFrames(SegmentIterator iterator) {
    List<String> items = new ArrayList<>();
    while (iterator.hasNext()) {
      items.add(new String(iterator.next()));
    }
    return items;
  }
}
