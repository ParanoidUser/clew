package dev.noid.clew.codec;

public interface JournalCodec<T> {

  byte[] encode(T entry);

  T decode(byte[] record);
}