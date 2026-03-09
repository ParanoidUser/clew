package dev.noid.clew;

public sealed interface WalEntry permits WalEntry.Push, WalEntry.Pop {

  record Push(String msg) implements WalEntry {

  }

  record Pop() implements WalEntry {

  }
}