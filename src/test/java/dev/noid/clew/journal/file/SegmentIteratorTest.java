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
    long pos = 0;
    pos = writeFrame(pos, "first");
    pos = writeFrame(pos, "second");
    pos = writeFrame(pos, "third");

    SegmentIterator iterator = new SegmentIterator(segment, 0, pos);
    assertEquals(List.of("first", "second", "third"), readAll(iterator));
  }

  @Test
  @DisplayName("starting from second frame offset skips the first")
  void startFromMiddle() {
    long second = writeFrame(0, "first");
    long end = writeFrame(second, "second");

    SegmentIterator iterator = new SegmentIterator(segment, second, end);
    assertEquals(List.of("second"), readAll(iterator));
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
    long pos = writeFrame(0, "a");
    pos = writeFrame(pos, "b");
    pos = writeFrame(pos, "c");

    SegmentIterator scanner = new SegmentIterator(segment, 0, pos);
    while (scanner.hasNext()) {
      // contract: if hasNext() returned true, next() must return a value without throwing
      assertDoesNotThrow(scanner::next);
    }
  }

  @Test
  @DisplayName("corruption is detected in hasNext() before it returns true")
  void corruptFrame_detectedInHasNext_notInNext() {
    long second = writeFrame(0, "good");
    long end = writeFrame(second, "corrupt");

    // corrupt a payload byte — magic and length remain intact, only checksum fails
    // offset into second frame: magic(4) + length(4) + 1 = 9
    segment.set(ValueLayout.JAVA_BYTE, (int) second + 9, (byte) 0xFF);

    SegmentIterator scanner = new SegmentIterator(segment, 0, end);
    scanner.next(); // consume first valid frame

    // After fix (Option B): hasNext() validates the frame and throws JCE before returning true
    // Currently: hasNext() returns true (magic present), next() throws JCE — contract violated
    assertThrows(JournalCorruptionException.class, scanner::hasNext);
  }

  @Test
  @DisplayName("corrupt frame mid-stream throws JournalCorruptionException")
  void corruptionMidStream() {
    long second = writeFrame(0, "good");
    long third = writeFrame(second, "will-corrupt");
    writeFrame(third, "unreachable");

    // flip a byte in the second frame's payload
    segment.set(ValueLayout.JAVA_BYTE, second + 5, (byte) 0xFF);

    var scanner = new SegmentIterator(segment, 0, segment.byteSize());

    assertArrayEquals("good".getBytes(), scanner.next());
    assertThrows(JournalCorruptionException.class, scanner::next);
  }

  @Test
  @DisplayName("corrupted magic on only frame within committed region throws JournalCorruptionException")
  void corruptedMagic_singleFrame_throwsCorruption() {
    long end = writeFrame(0, "data");

    segment.set(ValueLayout.JAVA_INT_UNALIGNED, 0, 0xDEADBEEF);
    var scanner = new SegmentIterator(segment, 0, end);
    assertThrows(JournalCorruptionException.class, scanner::next);
  }

  @Test
  @DisplayName("corrupted magic on middle frame within committed region throws JournalCorruptionException")
  void corruptedMagic_middleFrame_throwsCorruption() {
    long second = writeFrame(0, "first");
    long third = writeFrame(second, "second");
    long end = writeFrame(third, "third");

    segment.set(ValueLayout.JAVA_INT_UNALIGNED, (int) second, 0x00000000);
    var scanner = new SegmentIterator(segment, 0, end);
    scanner.next(); // consume the valid first frame
    assertThrows(JournalCorruptionException.class, scanner::next);
  }

  @Test
  @DisplayName("frames beyond limit are not visible (read isolation)")
  void readIsolation() {
    long second = writeFrame(0, "visible");
    writeFrame(second, "invisible");

    SegmentIterator iterator = new SegmentIterator(segment, 0, second);
    assertEquals(List.of("visible"), readAll(iterator));
  }

  private long writeFrame(long offset, String payload) {
    return offset + SegmentFrame.write(segment, offset, payload.getBytes());
  }

  private List<String> readAll(SegmentIterator iterator) {
    List<String> items = new ArrayList<>();
    while (iterator.hasNext()) {
      items.add(new String(iterator.next()));
    }
    return items;
  }
}
