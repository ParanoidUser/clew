package dev.noid.clew.journal;

/**
 * Signals that the journal's stored data has been corrupted or is structurally invalid.
 * <p>
 * This exception indicates that a record was successfully read from storage but failed integrity validation — for
 * example, a checksum mismatch, a malformed record frame, or an unparseable field. Unlike a general
 * {@link JournalException}, corruption means the data itself is damaged, not that the storage layer is temporarily
 * unavailable.
 * <p>
 * Callers that need to distinguish corruption from transient I/O failures can catch this type specifically. The
 * appropriate response is typically to halt processing and alert the operator, since the journal — the system's source
 * of truth — can no longer be trusted without repair or restoration from backup.
 *
 * @see JournalException
 * @see Journal
 */
public class JournalCorruptionException extends JournalException {

  public JournalCorruptionException(String message) {
    super(message);
  }

  public JournalCorruptionException(String message, Throwable cause) {
    super(message, cause);
  }
}
