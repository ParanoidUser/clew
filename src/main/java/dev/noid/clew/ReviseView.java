package dev.noid.clew;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import dev.noid.clew.ReviseState.Slot;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

public class ReviseView implements ReviseUi {

  @Override
  public void run(ReviseState state, ClewStack stack) throws IOException {
    try (Screen screen = new DefaultTerminalFactory().createScreen()) {
      screen.startScreen();
      loop(screen, state, stack);
    }
  }

  private void loop(Screen screen, ReviseState state, ClewStack stack) throws IOException {
    Slot source = null;
    while (true) {
      render(screen, state, source);
      KeyStroke key = screen.readInput();

      if (key.getKeyType() == KeyType.Escape) {
        if (source != null) {
          source = null;
          continue;
        }
        state.cancel();
        return;
      }

      Character c = key.getCharacter();
      if (c == null) continue;

      if (source == null) {
        switch (c) {
          case 'q' -> { state.cancel(); return; }
          case 'c' -> { state.commit(stack); return; }
          case 'e' -> {
            if (!state.main().isEmpty()) {
              String edited = promptEdit(screen, state.main().get(0));
              if (edited != null) state.edit(edited);
            }
          }
          case '1' -> source = Slot.MAIN;
          case '2' -> source = Slot.A;
          case '3' -> source = Slot.B;
        }
      } else {
        Slot dest = switch (c) {
          case '1' -> Slot.MAIN;
          case '2' -> Slot.A;
          case '3' -> Slot.B;
          default -> null;
        };
        if (dest != null) {
          try {
            state.move(source, dest);
          } catch (NoSuchElementException ignored) {
          }
          source = null;
        }
      }
    }
  }

  private void render(Screen screen, ReviseState state, Slot selected) throws IOException {
    screen.clear();
    TextGraphics g = screen.newTextGraphics();
    int cols = screen.getTerminalSize().getColumns();
    int rows = screen.getTerminalSize().getRows();
    int colW = cols / 3;

    drawHeader(g, 0,        colW, "[1] MAIN ("   + state.main().size()  + ")", selected == Slot.MAIN);
    drawHeader(g, colW,     colW, "[2] TEMP-A (" + state.tempA().size() + ")", selected == Slot.A);
    drawHeader(g, colW * 2, colW, "[3] TEMP-B (" + state.tempB().size() + ")", selected == Slot.B);

    drawItems(g, 2, 0,        colW, state.main(),  rows - 3);
    drawItems(g, 2, colW,     colW, state.tempA(), rows - 3);
    drawItems(g, 2, colW * 2, colW, state.tempB(), rows - 3);

    String hint = selected == null
        ? "[1/2/3] select source  [e] edit top  [c] commit  [q] cancel"
        : "Moving from " + selected + " -> [1/2/3] destination  [ESC] back";
    g.putString(0, rows - 1, hint);

    screen.refresh();
  }

  private void drawHeader(TextGraphics g, int col, int colW, String label, boolean active) {
    if (active) {
      g.setForegroundColor(TextColor.ANSI.BLACK);
      g.setBackgroundColor(TextColor.ANSI.WHITE);
    }
    String padded = String.format("%-" + colW + "s", label);
    g.putString(col, 0, padded.length() > colW ? padded.substring(0, colW) : padded);
    g.setForegroundColor(TextColor.ANSI.DEFAULT);
    g.setBackgroundColor(TextColor.ANSI.DEFAULT);
  }

  private void drawItems(TextGraphics g, int startRow, int col, int colW, List<String> items, int maxRows) {
    if (items.isEmpty()) {
      g.putString(col + 1, startRow, "(empty)");
      return;
    }
    int limit = Math.min(items.size(), maxRows);
    for (int i = 0; i < limit; i++) {
      String prefix = i == 0 ? "> " : "  ";
      String text = prefix + items.get(i);
      int maxLen = colW - 2;
      if (text.length() > maxLen) text = text.substring(0, maxLen - 3) + "...";
      g.putString(col + 1, startRow + i, text);
    }
  }

  private String promptEdit(Screen screen, String current) throws IOException {
    StringBuilder buf = new StringBuilder(current);
    while (true) {
      screen.clear();
      TextGraphics g = screen.newTextGraphics();
      int cols = screen.getTerminalSize().getColumns();
      int rows = screen.getTerminalSize().getRows();
      int mid = rows / 2;

      g.putString(0, mid - 1, "Edit top message:");
      String line = buf + "_";
      g.putString(0, mid, line.length() > cols ? line.substring(line.length() - cols) : line);
      g.putString(0, mid + 2, "[ENTER] confirm  [ESC] cancel  [BACKSPACE] delete");
      screen.refresh();

      KeyStroke key = screen.readInput();
      if (key.getKeyType() == KeyType.Enter) {
        String result = buf.toString().strip();
        return result.isEmpty() ? null : result;
      }
      if (key.getKeyType() == KeyType.Escape) return null;
      if (key.getKeyType() == KeyType.Backspace) {
        if (!buf.isEmpty()) buf.deleteCharAt(buf.length() - 1);
        continue;
      }
      Character c = key.getCharacter();
      if (c != null) buf.append(c);
    }
  }
}
