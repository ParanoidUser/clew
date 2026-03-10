package dev.noid.clew;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = JournalEntry.Push.class, name = "push"),
    @JsonSubTypes.Type(value = JournalEntry.Pop.class, name = "pop"),
    @JsonSubTypes.Type(value = JournalEntry.Drop.class, name = "drop")
})
public sealed interface JournalEntry permits
        JournalEntry.Push,
        JournalEntry.Pop,
        JournalEntry.Drop {

    record Push(String msg) implements JournalEntry {}

    record Pop() implements JournalEntry {}

    record Drop() implements JournalEntry {}
}