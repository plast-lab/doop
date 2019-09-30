package org.clyze.doop.common.scanner;

import java.io.*;
import java.util.*;
import org.clyze.doop.common.Database;

import static org.clyze.doop.common.PredicateFile.*;

/**
 * A binary analysis that is available to the native scanner.
 */
abstract class BinaryAnalysis {

    // The database object to use for writing facts.
    private final Database db;
    // The native code library.
    final String lib;
    // The entry points table.
    final SortedMap<Long, String> entryPoints = new TreeMap<>();

    BinaryAnalysis(Database db, String lib) {
        this.db = db;
        this.lib = lib;
    }

    /**
     * Return a set of the strings found in a binary.
     *
     * @return a map of address-to-string entries
     */
    abstract SortedMap<Long, String> findStrings() throws IOException;

    /**
     * Find string cross-references.
     *
     * @param binStrings   the string table (offset-string entries)
     * @return             a mapping from strings to functions containing data references to them
     */
    abstract Map<String, Set<String>> findXRefs(Map<Long, String> binStrings) throws IOException;

    /**
     * Initialize the entry points table of the library.
     */
    abstract void initEntryPoints() throws IOException;

    /**
     * Returns a list of pointer values that may point to global data.
     */
    abstract Set<Long> getGlobalDataPointers() throws IOException;

    /**
     * Write the entry points of the library.
     */
    void writeEntryPoints() {
        entryPoints.forEach((addr, name) ->
                            db.add(NATIVE_LIB_ENTRY_POINT, lib, name, String.valueOf(addr)));
    }

    // Auxiliary hex converter.
    static long hexToLong(String s) {
        if (s.startsWith("0x"))
            s = s.substring(2);
        return Long.parseLong(s.trim(), 16);
    }
}
