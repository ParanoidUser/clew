package dev.noid.clew.journal;

import java.util.List;
import java.util.stream.Stream;

/**
 * Append-only log providing a total ordering of opaque binary records.
 * <p>
 * The journal is the system's single source of truth. It records a linear sequence of records that can be replayed to
 * reconstruct the current state. The journal is intentionally schema-agnostic — it stores and retrieves raw
 * {@code byte[]} records without interpreting their contents. Serialization, deserialization, and schema versioning are
 * the responsibility of a higher-level codec layer.
 *
 * <h3>Core Guarantees</h3>
 * <ul>
 * <li><b>Linearizability:</b> Each record is assigned a monotonically increasing {@code long} position. If a record is
 *     committed at position {@code n}, all subsequent commits are guaranteed to have a position {@code > n}.</li>
 * <li><b>Atomic Persistence:</b> A batch of records provided to {@link #append(List)} is treated as a single atomic
 *     unit — either the entire batch is persisted and visible to readers, or none of it is.</li>
 * <li><b>Immutability:</b> Once a record is committed, it cannot be modified or deleted. Positions are stable and the
 *     history is write-once.</li>
 * <li><b>Durability:</b> A successful {@link #append(List)} guarantees that the data has been handed off to the
 *     underlying storage layer according to the implementation's durability policy.</li>
 * </ul>
 *
 * <h3>Exception Model</h3>
 * <p>
 * All journal operations throw unchecked {@link JournalException} on failure. Intermediate layers are not forced to
 * declare or handle these exceptions — only the outermost application boundary is expected to catch and translate them
 * into user-facing messages.
 * <ul>
 * <li>{@link JournalException} — operational failures: I/O errors, permission issues, unavailable storage.</li>
 * <li>{@link JournalCorruptionException} — data integrity violations: checksum mismatches, malformed records. Indicates
 *     the journal's data is damaged and cannot be trusted without repair.</li>
 * </ul>
 *
 * <h3>Resource Management</h3>
 * <p>
 * Implementations may hold system resources (file handles, network connections). Callers <b>must</b> close streams
 * returned by {@link #openStream(long)} using {@code try-with-resources}.
 *
 * @see JournalException
 * @see JournalCorruptionException
 */
public interface Journal extends AutoCloseable {

  /**
   * Appends a batch of records to the journal as a single atomic unit.
   * <p>
   * All records in the batch are committed together - either all are visible to readers or none are.
   *
   * @param records the ordered list of records to append; must not be null or contain null elements.
   * @return an opaque position cursor pointing past this batch; pass to {@link #openStream(long)} to read only records
   * appended after this call.
   * @throws IllegalArgumentException if {@code records} is null or contains null elements.
   * @throws JournalException         if the batch cannot be persisted.
   */
  long append(List<byte[]> records);

  /**
   * Opens a forward-reading stream of records starting at the given position.
   * <p>
   * The returned stream is lazy and backed by I/O resources. Callers <b>must</b> close it using
   * {@code try-with-resources}. Corruption detected during iteration is signaled by
   * {@link JournalCorruptionException}.
   *
   * @param fromPosition an opaque position cursor: either {@code 0} to read from the beginning, or a value previously
   *                     returned by {@link #append} or {@link #currentPosition}.
   * @return a stream of records; empty if the position is at the current end. Corruption detected during iteration
   * throws {@link JournalCorruptionException}.
   * @throws IllegalArgumentException if {@code fromPosition} was not produced by this journal.
   * @throws JournalException         if the storage layer cannot be read.
   */
  Stream<byte[]> openStream(long fromPosition);

  /**
   * Returns a position cursor representing the end of the last committed record. Pass directly to {@link #openStream}
   * to read only new records without replaying history.
   * <p>
   *
   * @return a position cursor; passing it to {@link #openStream} yields an empty stream if no new records have been
   * appended.
   * @throws JournalException if the position cannot be determined.
   */
  long currentPosition();

  /**
   * Releases resources held by this journal (file handles, connections).
   *
   * @throws JournalException if resources cannot be cleanly released.
   */
  @Override
  void close();

}
