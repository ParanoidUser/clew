package dev.noid.clew;

import java.nio.file.Path;

public class Clew {

  public static void main(String[] args) throws Exception {
    Path clewDir = Path.of(System.getProperty("user.home"), ".clew");
    DiskJournal wal = new DiskJournal(clewDir.resolve("wal"));
    Path scratchFile = clewDir.resolve("revise.tmp");
    CommandHandler handler = new CommandHandler(
        args, new ClewStack(wal), wal, scratchFile, new ReviseView(), System.out, System.err);
    System.exit(handler.invoke());
  }
}
