package dev.noid.clew;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.zip.CRC32C;

public class DiskJournal implements Journal {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ReentrantLock appendLock = new ReentrantLock();
  private final Path filePath;
  private final FileChannel appendChannel;
  private volatile long committedPosition;

  public DiskJournal(Path filePath) throws JournalException {
    try {
      if (filePath.getParent() != null) {
        Files.createDirectories(filePath.getParent());
      }

      this.appendChannel = FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      this.filePath = filePath;
    } catch (AccessDeniedException cause) {
      throw new JournalException("Access denied: Cannot initialize journal at " + filePath, cause);
    } catch (IOException cause) {
      throw new JournalException("I/O failure: Could not initialize journal at " + filePath, cause);
    }
  }

  @Override
  public long append(List<? extends JournalEntry> entries) throws JournalException {
    appendLock.lock();
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      for (var entry : entries) {
        byte[] jsonBytes = MAPPER.writeValueAsBytes(entry);
        long crc = calculateHash(jsonBytes);
        bos.write(jsonBytes);
        bos.write('|');
        bos.write(Long.toHexString(crc).getBytes(StandardCharsets.UTF_8));
        bos.write('\n');
      }

      ByteBuffer buf = ByteBuffer.wrap(bos.toByteArray());
      while (buf.hasRemaining()) {
        appendChannel.write(buf);
      }

      appendChannel.force(false); // Explicit durability
      committedPosition = appendChannel.size();
      return committedPosition;
    } catch (IOException e) {
      throw new JournalException("Write failure: Failed to commit batch to " + filePath, e);
    } finally {
      appendLock.unlock();
    }
  }

  @Override
  public Stream<JournalEntry> openStream(long fromPosition) throws JournalException {
    if (fromPosition < 0) {
      throw new JournalException("Invalid position: %d (must be >= 0)".formatted(fromPosition));
    }

    BufferedReader reader = openReader(fromPosition);
    if (reader == null) {
      return Stream.empty();
    }

    return reader.lines()
        .map(line -> {
          try {
            return parseLine(line);
          } catch (JournalException cause) {
            throw new RuntimeException(cause);
          }
        })
        .onClose(() -> {
          try {
            reader.close();
          } catch (IOException cause) { /* ignored */}
        });
  }

  @Override
  public long getEndPosition() throws JournalException {
    return committedPosition;
  }

  private BufferedReader openReader(long fromPosition) throws JournalException {
    FileChannel channel = null;
    try {
      channel = FileChannel.open(filePath, StandardOpenOption.READ);
      if (fromPosition == channel.size()) {
        channel.close();
        return null;
      }
      if (fromPosition > channel.size()) {
        throw new JournalException("Position %d is beyond journal end (%d).".formatted(fromPosition, channel.size()));
      }
      channel.position(fromPosition);
      return new BufferedReader(Channels.newReader(channel, StandardCharsets.UTF_8));
    } catch (IOException cause) {
      if (channel != null) {
        try {
          channel.close();
        } catch (IOException ignored) {
        }
      }
      if (cause instanceof JournalException internal) {
        throw internal;
      }
      throw new JournalException("Read failure at position " + fromPosition, cause);
    }
  }

  private JournalEntry parseLine(String line) throws JournalException {
    int separator = line.lastIndexOf('|');
    if (separator == -1) {
      throw new JournalException("Corruption: No checksum separator found in entry.");
    }

    String json = line.substring(0, separator);
    long hash = Long.parseLong(line.substring(separator + 1), 16);

    if (calculateHash(json.getBytes()) != hash) {
      throw new JournalException("Corruption: Integrity check failed (Checksum mismatch).");
    }
    try {
      return MAPPER.readValue(json, JournalEntry.class);
    } catch (JacksonException cause) {
      throw new JournalException("Corruption: Failed to decode journal entry.", cause);
    }
  }

  private long calculateHash(byte[] data) {
    CRC32C crc = new CRC32C();
    crc.update(data);
    return crc.getValue();
  }
}
