package dev.noid.clew;

import java.io.IOException;

public class JournalException extends IOException {

  public JournalException(String message) {
    super(message);
  }

  public JournalException(String message, IOException cause) {
    super(message, cause);
  }
}
