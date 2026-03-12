package dev.noid.clew.cli;

import dev.noid.clew.UserIntent;
import java.util.function.Function;

record CommandSpec(
    String label,
    String alias,
    String usage,
    String description,
    Function<String[], UserIntent> mapper
) {}