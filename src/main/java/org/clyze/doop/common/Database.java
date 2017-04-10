package org.clyze.doop.common;

import java.io.Closeable;
import java.io.File;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public class Database implements Closeable, Flushable {
    private static final char SEP = '\t';
    private static final char EOL = '\n';
    private File directory = null;

    private final Map<PredicateFile, Writer> _writers;

    public Database(File directory) throws IOException {
        this.directory = directory;
        this._writers = new EnumMap<>(PredicateFile.class);

        for(PredicateFile predicateFile : EnumSet.allOf(PredicateFile.class))
            _writers.put(predicateFile, predicateFile.getWriter(this.directory, ".facts"));
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
        // Quote some special characters.
        // TODO: is this worth optimizing?
        String data = column
            .replaceAll("\"", "\\\\\"")
            .replaceAll("\n", "\\\\n")
            .replaceAll("\t", "\\\\t");
        return data;
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
