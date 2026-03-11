package dev.noid.clew.cli;

import dev.noid.clew.stack.ReviseState;
import java.io.IOException;

@FunctionalInterface
public interface ReviseUi {

  void run(ReviseState state) throws IOException;
}
