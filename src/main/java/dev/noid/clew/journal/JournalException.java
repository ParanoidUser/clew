package dev.noid.clew.journal;

/**
 * Signals that a journal operation has failed.
 * <p>
 * This is the base exception for all journal-related failures, including I/O errors, permission issues, and
 * configuration problems. Callers interacting with a {@link Journal} should catch this type to handle operational
 * failures that may be transient or environment-dependent (e.g., disk full, access denied).
 * <p>
 * This exception is unchecked. Intermediate layers are not forced to declare or handle it — only the outermost
 * boundary (e.g., a CLI handler) is expected to catch and translate it into a user-facing message.
 *
 * @see JournalCorruptionException
 * @see Journal
 */
public class JournalException extends RuntimeException {

  public JournalException(String message) {
    super(message);
  }

  public JournalException(String message, Throwable cause) {
    super(message, cause);
  }
}
