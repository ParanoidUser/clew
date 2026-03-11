package dev.noid.clew.codec;

import dev.noid.clew.JournalRecord;

public interface JournalCodec<T extends JournalRecord> {

  byte[] encode(T entry);

  T decode(byte[] record);
}