package dev.noid.clew.codec;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.JournalRecord;
import dev.noid.clew.JournalRecord.Drop;
import dev.noid.clew.JournalRecord.Pop;
import dev.noid.clew.JournalRecord.Push;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JacksonJournalCodecTest {

  private final JacksonJournalCodec<JournalRecord> codec = new JacksonJournalCodec<>(JournalRecord.class);

  @Test
  @DisplayName("Push round-trips with correct message")
  void pushRoundTrip() {
    Push original = new Push("hello");
    JournalRecord decoded = codec.decode(codec.encode(original));
    assertEquals(original, decoded);
  }

  @Test
  @DisplayName("Pop round-trips")
  void popRoundTrip() {
    Pop original = new Pop();
    JournalRecord decoded = codec.decode(codec.encode(original));
    assertEquals(original, decoded);
  }

  @Test
  @DisplayName("Drop round-trips")
  void dropRoundTrip() {
    Drop original = new Drop();
    JournalRecord decoded = codec.decode(codec.encode(original));
    assertEquals(original, decoded);
  }

  @Test
  @DisplayName("special characters in message survive round-trip")
  void specialCharacters() {
    String nasty = "it's a \"test\" with \\backslashes\\ and\nnewlines and \uD83D\uDE00 emoji";
    Push original = new Push(nasty);
    JournalRecord decoded = codec.decode(codec.encode(original));
    assertInstanceOf(Push.class, decoded);
    assertEquals(nasty, ((Push) decoded).msg());
  }

  @Test
  @DisplayName("garbage bytes throw on decode")
  void garbageBytesThrow() {
    byte[] garbage = new byte[]{0x00, 0x01, 0x02};
    assertThrows(RuntimeException.class, () -> codec.decode(garbage));
  }

  @Test
  @DisplayName("unknown type field throws on decode")
  void unknownTypeThrows() {
    byte[] unknown = "{\"type\":\"reorder\"}".getBytes(StandardCharsets.UTF_8);
    assertThrows(RuntimeException.class, () -> codec.decode(unknown));
  }
}
