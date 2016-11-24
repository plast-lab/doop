package org.clyze.doop.soot;

import java.io.Closeable;
import java.io.File;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

class Database implements Closeable, Flushable {
    private static final char SEP = '\t';
    private static final char EOL = '\n';

    private final Map<PredicateFile, Writer> _writers;

    Database(File directory) throws IOException {
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


    private String addColumn(String column) throws IOException {
        // Quote some special characters
        String data = column
            .replaceAll("\"", "\\\\\"")
            .replaceAll("\n", "\\\\n")
            .replaceAll("\t", "\\\\t");
        return data;
    }


    public void add(PredicateFile predicateFile, String arg, String... args) {
        try {
            String line = addColumn(arg);
            for (String col : args)
                line = line + SEP + addColumn(col);
            line = line + EOL;
            Writer writer = _writers.get(predicateFile);
            synchronized(predicateFile) {
                writer.write(line);
            }
        } catch(IOException exc) {
            throw new RuntimeException(exc);
        }
    }
}
