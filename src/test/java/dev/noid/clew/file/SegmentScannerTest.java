package dev.noid.clew.file;

import dev.noid.clew.journal.JournalCorruptionException;
import dev.noid.clew.journal.file.SegmentFrame;
import dev.noid.clew.journal.file.SegmentScanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SegmentScannerTest {

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
    List<byte[]> results = new SegmentScanner(segment, 0, 0).stream().toList();
    assertTrue(results.isEmpty());
  }

  @Test
  @DisplayName("three frames are yielded in order")
  void sequentialScan() {
    long pos = 0;
    pos = writeFrame(pos, "first");
    pos = writeFrame(pos, "second");
    pos = writeFrame(pos, "third");

    List<String> results = new SegmentScanner(segment, 0, pos).stream()
        .map(String::new)
        .toList();

    assertEquals(List.of("first", "second", "third"), results);
  }

  @Test
  @DisplayName("starting from second frame offset skips the first")
  void startFromMiddle() {
    long second = writeFrame(0, "first");
    long end = writeFrame(second, "second");

    List<String> results = new SegmentScanner(segment, second, end).stream()
        .map(String::new)
        .toList();

    assertEquals(List.of("second"), results);
  }

  @Test
  @DisplayName("starting at limit (caught up) produces empty stream")
  void startAtLimit() {
    long end = writeFrame(0, "data");

    List<byte[]> results = new SegmentScanner(segment, end, end).stream().toList();
    assertTrue(results.isEmpty());
  }

  @Test
  @DisplayName("corrupt frame mid-stream throws JournalCorruptionException")
  void corruptionMidStream() {
    long second = writeFrame(0, "good");
    long third = writeFrame(second, "will-corrupt");
    writeFrame(third, "unreachable");

    // flip a byte in the second frame's payload
    segment.set(ValueLayout.JAVA_BYTE, second + 5, (byte) 0xFF);

    var scanner = new SegmentScanner(segment, 0, segment.byteSize());
    var stream = scanner.stream().iterator();

    assertArrayEquals("good".getBytes(), stream.next());
    assertThrows(JournalCorruptionException.class, stream::next);
  }

  @Test
  @DisplayName("frames beyond limit are not visible (read isolation)")
  void readIsolation() {
    long second = writeFrame(0, "visible");
    writeFrame(second, "invisible");

    List<String> results = new SegmentScanner(segment, 0, second).stream()
        .map(String::new)
        .toList();

    assertEquals(List.of("visible"), results);
  }

  private long writeFrame(long offset, String payload) {
    SegmentFrame frame = new SegmentFrame(segment, offset);
    return offset + frame.write(payload.getBytes());
  }
}
