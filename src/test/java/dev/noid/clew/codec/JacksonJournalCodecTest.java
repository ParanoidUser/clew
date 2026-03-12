package dev.noid.clew.codec;

import static org.junit.jupiter.api.Assertions.*;

import dev.noid.clew.TaskEvent;
import dev.noid.clew.TaskEvent.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JacksonJournalCodecTest {

  private final JacksonJournalCodec<TaskEvent> codec = new JacksonJournalCodec<>(TaskEvent.class);

  @Test
  @DisplayName("TaskCreated round-trips")
  void taskCreatedRoundTrip() {
    TaskCreated original = new TaskCreated(1000L, "id-1", "hello");
    TaskEvent decoded = codec.decode(codec.encode(original));
    assertEquals(original, decoded);
  }

  @Test
  @DisplayName("TaskCompleted round-trips")
  void taskCompletedRoundTrip() {
    TaskCompleted original = new TaskCompleted(1000L, "id-1");
    TaskEvent decoded = codec.decode(codec.encode(original));
    assertEquals(original, decoded);
  }

  @Test
  @DisplayName("TaskDropped round-trips")
  void taskDroppedRoundTrip() {
    TaskDropped original = new TaskDropped(1000L, "id-1");
    TaskEvent decoded = codec.decode(codec.encode(original));
    assertEquals(original, decoded);
  }

  @Test
  @DisplayName("TaskActivated round-trips")
  void taskActivatedRoundTrip() {
    TaskActivated original = new TaskActivated(1000L, "id-1");
    TaskEvent decoded = codec.decode(codec.encode(original));
    assertEquals(original, decoded);
  }

  @Test
  @DisplayName("TaskDeactivated round-trips")
  void taskDeactivatedRoundTrip() {
    TaskDeactivated original = new TaskDeactivated(1000L, "id-1");
    TaskEvent decoded = codec.decode(codec.encode(original));
    assertEquals(original, decoded);
  }

  @Test
  @DisplayName("TaskContentUpdated round-trips")
  void taskContentUpdatedRoundTrip() {
    TaskContentUpdated original = new TaskContentUpdated(1000L, "id-1", "new desc");
    TaskEvent decoded = codec.decode(codec.encode(original));
    assertEquals(original, decoded);
  }

  @Test
  @DisplayName("special characters in description survive round-trip")
  void specialCharacters() {
    String nasty = "it's a \"test\" with \\backslashes\\ and\nnewlines and \uD83D\uDE00 emoji";
    TaskCreated original = new TaskCreated(1000L, "id-1", nasty);
    TaskEvent decoded = codec.decode(codec.encode(original));
    assertInstanceOf(TaskCreated.class, decoded);
    assertEquals(nasty, ((TaskCreated) decoded).description());
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
