package dev.noid.clew.codec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noid.clew.TaskEvent;
import java.io.IOException;

public final class JacksonJournalCodec<T> implements JournalCodec<T> {

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
      @JsonSubTypes.Type(value = TaskEvent.TaskCreated.class, name = "task_created"),
      @JsonSubTypes.Type(value = TaskEvent.TaskCompleted.class, name = "task_completed"),
      @JsonSubTypes.Type(value = TaskEvent.TaskDropped.class, name = "task_dropped"),
      @JsonSubTypes.Type(value = TaskEvent.TaskActivated.class, name = "task_activated"),
      @JsonSubTypes.Type(value = TaskEvent.TaskDeactivated.class, name = "task_deactivated"),
      @JsonSubTypes.Type(value = TaskEvent.TaskContentUpdated.class, name = "task_content_updated"),
  })
  private interface TaskEventMixin {}

  private final ObjectMapper mapper;
  private final Class<T> baseClass;

  public JacksonJournalCodec(Class<T> baseClass) {
    this.baseClass = baseClass;
    this.mapper = new ObjectMapper()
        .addMixIn(TaskEvent.class, TaskEventMixin.class)
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
