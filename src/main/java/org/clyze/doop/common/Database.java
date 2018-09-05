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
        StringBuilder sb = new StringBuilder();
        for (char c : column.toCharArray())
            switch (c) {
                case '\"':
                    sb.append("\\\\\"");
                    break;
                case '\n':
                    sb.append("\\\\n");
                    break;
                case '\t':
                    sb.append("\\\\t");
                    break;
                default:
                    sb.append(c);
            }
        return sb.toString();
    }

    public void add(PredicateFile predicateFile, String arg, String... args) {
        try {
            StringBuilder line = new StringBuilder(addColumn(arg));
            for (String col : args) {
                line.append(SEP);
                line.append(addColumn(col));
            }
            line.append(EOL);
            Writer writer = _writers.get(predicateFile);
            synchronized(predicateFile) {
                writer.write(line.toString());
            }
        } catch(IOException exc) {
            throw new RuntimeException(exc);
        }
    }
}
