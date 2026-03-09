package dev.noid.clew;

import java.nio.file.Path;

public class Clew {

  public static void main(String[] args) {
    Path clewDir = Path.of(System.getProperty("user.home"), ".clew");
    Wal wal = new Wal(clewDir.resolve("wal"));
    Path scratchFile = clewDir.resolve("revise.tmp");
    CommandHandler handler = new CommandHandler(
        args, new ClewStack(wal), scratchFile, new ReviseView(), System.out, System.err);
    System.exit(handler.invoke());
  }
}
