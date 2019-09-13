package org.clyze.doop.python.utils;


import java.io.*;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;


public class PythonDatabase implements Closeable, Flushable {
    private static final char SEP = '\t';
    private static final char EOL = '\n';

    private final Map<PythonPredicateFile, Writer> _writers;

    public PythonDatabase(File directory) throws IOException {
        this._writers = new EnumMap<>(PythonPredicateFile.class);

        for(PythonPredicateFile predicateFile : EnumSet.allOf(PythonPredicateFile.class)) {
            _writers.put(predicateFile, predicateFile.getWriter(directory, ".facts"));
        }
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
        // TODO: is this worth optimizing?
        return column
                .replaceAll("\"", "\\\\\"")
                .replaceAll("\n", "\\\\n")
                .replaceAll("\t", "\\\\t");
    }


    public void add(PythonPredicateFile predicateFile, String arg, String... args) {
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