package dev.noid.clew;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noid.clew.WalEntry.Pop;
import dev.noid.clew.WalEntry.Push;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class Wal {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final Path filePath;

  public Wal(Path filePath) {
    this.filePath = filePath;
  }

  public void append(WalEntry entry) {
    try {
      if (!Files.exists(filePath)) {
        Files.createDirectories(filePath.getParent());
      }

      WalEntryDto dto = switch (entry) {
        case Push push -> new WalEntryDto("PUSH", push.msg());
        case Pop pop -> new WalEntryDto("POP", "");
      };

      String json = MAPPER.writeValueAsString(dto);
      Files.writeString(filePath, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException cause) {
      throw new UncheckedIOException(cause);
    }
  }

  public List<WalEntry> readAll() {
    if (!Files.exists(filePath)) {
      return List.of();
    }

    try {
      List<String> jsonl = Files.readAllLines(filePath);
      List<WalEntry> entries = new ArrayList<>();
      for (String json : jsonl) {
        WalEntryDto dto = MAPPER.readValue(json, WalEntryDto.class);
        WalEntry entry = switch (dto.op) {
          case "PUSH" -> new Push(dto.msg);
          case "POP" -> new Pop();
          default -> throw new UnsupportedOperationException();
        };
        entries.add(entry);
      }
      return entries;
    } catch (IOException cause) {
      throw new UncheckedIOException(cause);
    }
  }

  private record WalEntryDto(String op, String msg) {

  }
}
