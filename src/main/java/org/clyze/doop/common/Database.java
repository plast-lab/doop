package org.clyze.doop.common;

import java.io.*;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public class Database implements Closeable, Flushable {
    private static final char SEP = '\t';
    private static final char EOL = '\n';

    private final Map<PredicateFile, Writer> _writers;

    public Database(File directory) throws IOException {
        this._writers = new EnumMap<>(PredicateFile.class);

        for(PredicateFile predicateFile : EnumSet.allOf(PredicateFile.class))
            _writers.put(predicateFile, predicateFile.getWriter(directory, ".facts"));
    }

    @Override
    public void close() throws IOException {
        for(Writer w: _writers.values())
            w.close();
    }

    @Override
    public void flush() throws IOException {
        for(Writer w: _writers.values())
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
        try {
            StringBuilder line = new StringBuilder(addColumn(arg));
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
