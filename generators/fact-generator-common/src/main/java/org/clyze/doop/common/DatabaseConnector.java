package org.clyze.doop.common;

import java.util.*;
import org.clyze.scanner.NativeDatabaseConsumer;

public class DatabaseConnector implements NativeDatabaseConsumer {
    private final Database db;
    private final Map<String, PredicateFile> predicates = new HashMap<>();

    public DatabaseConnector(Database db) {
        this.db = db;
    }

    public void add(String pfName, String arg, String... args) {
        if (pfName != null) {
            db.add(predicates.computeIfAbsent(pfName, PredicateFile::valueOf), arg, args);
        } else
            System.err.println("ERROR: no output predicate is available.");
    }

}
