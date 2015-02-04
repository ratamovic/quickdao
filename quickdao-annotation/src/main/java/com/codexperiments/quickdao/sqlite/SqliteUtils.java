package com.codexperiments.quickdao.sqlite;

import static java.lang.String.format;

public class SqliteUtils {
    public static void validateInsertion(long id, long version) {
        if (id < 0) throw new IllegalArgumentException(format("Id %s is cannot be inserted", id));
        if (version != 0) throw new IllegalArgumentException(format("Version %s cannot be inserted", version));
    }

    public static long validateUpdate(long id, long version) {
        if (id < 0) throw new IllegalArgumentException(format("Id %s cannot be updated", id));
        if (version <= 0) throw new IllegalArgumentException(format("Version %s cannot be updated", version));
        return version;
    }
}
