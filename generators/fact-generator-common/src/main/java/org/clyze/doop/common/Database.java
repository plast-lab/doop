package org.clyze.doop.common;

import java.io.*;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.nio.file.Paths;

public class Database implements Closeable, Flushable {
    private static final char SEP = '\t';
    private static final char EOL = '\n';

    private final Map<PredicateFile, Writer> _writers;
    private final String directory;

    /**
     * Generate a database object, which can be used to write facts.
     *
     * @param directory     the output directory
     */
    public Database(String directory) throws IOException {
        this(directory, true);
    }

    /**
     * Generate a database object, which can be used to write facts.
     *
     * @param directory     the output directory
     * @param initWriters   if false, no facts can be written (dummy database)
     */
    public Database(String directory, boolean initWriters) throws IOException {
        this.directory = directory;

        if (!initWriters) {
            this._writers = null;
            return;
        }

        this._writers = new EnumMap<>(PredicateFile.class);

        for (PredicateFile predicateFile : EnumSet.allOf(PredicateFile.class)) {
            _writers.put(predicateFile, predicateFile.getWriter(new File(directory), ".facts"));
            File factsFile = new File(String.valueOf(Paths.get(directory, PredicateFile.valueOf(predicateFile.name()) + ".facts")));
            // if (factsFile.exists()) {
            //     factsFile.delete();
            // }
            if (factsFile.createNewFile()) {
                System.out.println("Created missing facts file: " + factsFile.getPath());
            }
        }

    }

    public String getDirectory() {
        return directory;
    }

    @Override
    public void close() throws IOException {
        if (_writers != null)
            for (Writer w: _writers.values())
                w.close();
    }

    @Override
    public void flush() throws IOException {
        if (_writers != null)
            for (Writer w: _writers.values())
                w.flush();
    }

    private String addColumn(String column) {
        // Quote some special characters.
        final char SLASH = '\"';
        final char EOL = '\n';
        final char TAB = '\t';
        if ((column.indexOf(SLASH) >= 0) ||
            (column.indexOf(EOL)   >= 0) ||
            (column.indexOf(TAB)   >= 0)) {
            // Assume at most 5 special characters will be rewritten
            // before updating the capacity.
            StringBuilder sb = new StringBuilder(column.length() + 5);
            for (char c : column.toCharArray())
                switch (c) {
                case SLASH:
                    sb.append("\\\\\"");
                    break;
                case EOL:
                    sb.append("\\\\n");
                    break;
                case TAB:
                    sb.append("\\\\t");
                    break;
                default:
                    sb.append(c);
                }
            return sb.toString();
        } else
            return column;
    }

    public void add(PredicateFile predicateFile, String arg, String... args) {
        if (_writers == null)
            return;
        try {
            // Estimate StringBuilder capacity.
            int capacity = args.length + 1;
            for (String arg0 : args)
                capacity += arg0.length();

            StringBuilder line = new StringBuilder(capacity);
            line.append(addColumn(arg));
            for (String col : args) {
                line.append(SEP);
                line.append(addColumn(col));
            }
            line.append(EOL);
            String strLine = line.toString();
            Writer writer = _writers.get(predicateFile);
            synchronized(predicateFile) {
                writer.write(strLine);
            }
        } catch(IOException exc) {
            throw new RuntimeException(exc);
        }
    }
}
