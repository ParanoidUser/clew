package dev.noid.clew;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = JournalRecord.Push.class, name = "push"),
    @JsonSubTypes.Type(value = JournalRecord.Pop.class, name = "pop"),
    @JsonSubTypes.Type(value = JournalRecord.Drop.class, name = "drop")
})
public sealed interface JournalRecord permits
        JournalRecord.Push,
        JournalRecord.Pop,
        JournalRecord.Drop {

    record Push(String msg) implements JournalRecord {}

    record Pop() implements JournalRecord {}

    record Drop() implements JournalRecord {}
}