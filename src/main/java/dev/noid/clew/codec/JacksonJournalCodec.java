package dev.noid.clew.codec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noid.clew.JournalRecord;
import java.io.IOException;

public final class JacksonJournalCodec<T extends JournalRecord> implements JournalCodec<T> {

  /**
   * The "Mix-in" interface acts as a shadow for the real record. Jackson will apply these annotations to JournalRecord
   * at runtime.
   */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
      @JsonSubTypes.Type(value = JournalRecord.Push.class, name = "push"),
      @JsonSubTypes.Type(value = JournalRecord.Pop.class, name = "pop"),
      @JsonSubTypes.Type(value = JournalRecord.Drop.class, name = "drop")
  })
  private interface JournalRecordMixin {}

  private final ObjectMapper mapper;
  private final Class<T> baseClass;

  public JacksonJournalCodec(Class<T> baseClass) {
    this.baseClass = baseClass;
    this.mapper = new ObjectMapper()
        .addMixIn(JournalRecord.class, JournalRecordMixin.class)
        .findAndRegisterModules();
  }

  @Override
  public byte[] encode(T record) {
    try {
      return mapper.writeValueAsBytes(record);
    } catch (JsonProcessingException cause) {
      throw new RuntimeException("Failed to serialize record", cause);
    }
  }

  @Override
  public T decode(byte[] bytes) {
    try {
      return mapper.readValue(bytes, baseClass);
    } catch (IOException cause) {
      throw new RuntimeException("Failed to deserialize record", cause);
    }
  }
}