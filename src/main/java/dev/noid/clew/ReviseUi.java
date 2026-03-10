package dev.noid.clew;

import java.io.IOException;

@FunctionalInterface
public interface ReviseUi {
  void run(ReviseState state) throws IOException;
}
