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

    private File directory;
    private Map<PredicateFile, Writer> writers;

    Database(File directory) throws IOException {
        this.directory = directory;
        this.writers = new EnumMap<>(PredicateFile.class);

        for(PredicateFile predicateFile : EnumSet.allOf(PredicateFile.class))
            writers.put(predicateFile, predicateFile.getWriter(directory, ".facts"));
    }

    @Override
    public void close() throws IOException {
        for(Writer w: writers.values())
            w.close();
    }

    @Override
    public void flush() throws IOException {
        for(Writer w: writers.values())
            w.flush();
    }


    private void addColumn(Writer writer, String column) throws IOException {
        // Quote some special characters
        String data = column
            .replaceAll("\"", "\\\\\"")
            .replaceAll("\n", "\\\\n")
            .replaceAll("\t", "\\\\t");

        writer.write(data);
    }


    public void add(PredicateFile predicateFile, String arg, String... args) {
        try {
            synchronized(predicateFile) {
                Writer writer = writers.get(predicateFile);
                addColumn(writer, arg);
                for (String col : args)
                    addColumn(writer.append(SEP), col);

                writer.write(EOL);
            }
        } catch(IOException exc) {
            throw new RuntimeException(exc);
        }
    }
}
