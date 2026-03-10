package dev.noid.clew;

import java.util.List;

import java.util.stream.Stream;

/**
 * Append-only transaction log providing a total ordering of opaque binary records.
 * <p>
 * The Journal serves as the system's "Source of Truth." It records a linear sequence of records that can be replayed to
 * reconstruct the state of a distributed system or persistent data structure. The Journal is intentionally
 * schema-agnostic; it stores and retrieves raw {@code byte[]} records without interpreting their contents.
 * Serialization, deserialization, and schema versioning are the responsibility of a higher-level codec layer.
 * <h3>Core Guarantees:</h3>
 * <ul>
 * <li><b>Linearizability:</b> Records are assigned a unique, monotonically increasing
 * {@code long} position. If a record is reported at position {@code n}, all subsequent
 * appends are guaranteed to have a position {@code > n}.</li>
 * <li><b>Atomic Persistence:</b> A batch of records provided to {@link #append(List)} is
 * treated as a single atomic unit. The journal ensures that either the entire batch is
 * persisted and made visible to readers, or none of it is.</li>
 * <li><b>Immutability:</b> Once a record is committed and assigned a position, it cannot
 * be modified or deleted. The history is stable and "write-once."</li>
 * <li><b>Durability:</b> Successful completion of an append operation confirms that
 * the data has been handed off to the underlying storage layer according to the implementation's safety policy.</li>
 * </ul>
 * <h3>Resource Management:</h3>
 * <p>
 * Implementations may hold sensitive system resources (file handles, network sockets,
 * or subprocesses). Callers <b>must</b> use {@code try-with-resources} when opening
 * streams via {@link #openStream(long)} and when managing the lifecycle of the
 * journal itself.
 *
 * @see <a href="https://martinfowler.com/eaaDev/EventSourcing.html">Event Sourcing Pattern</a>
 */
public interface Journal {

  /**
   * Appends a sequence of records to the journal as a single atomic unit.
   *
   * @param records the ordered list of records to append.
   * @return the position past the last record in this batch.
   * @throws JournalException if the journal cannot guarantee persistence.
   */
  long append(List<byte[]> records) throws JournalException;

  /**
   * Opens a stream of records starting at the specified position.
   *
   * @param fromPosition the inclusive starting position (0-based).
   * @return a stream of records; empty if the position is at or past the end.
   * @throws JournalException if the position is invalid or data is unreadable.
   */
  Stream<byte[]> openStream(long fromPosition) throws JournalException;

  /**
   * Returns the position past the last committed record. Can be passed directly to {@link #openStream(long)} to read
   * only new records.
   *
   * @return 0 if the journal is empty, otherwise the position after the last committed record.
   * @throws JournalException if the storage layer is unreachable.
   */
  long getEndPosition() throws JournalException;
}
