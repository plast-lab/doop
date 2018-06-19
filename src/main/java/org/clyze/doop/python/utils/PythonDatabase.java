package org.clyze.doop.python.utils;


import java.io.*;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;


public class PythonDatabase implements Closeable, Flushable {
    private static final char SEP = '\t';
    private static final char EOL = '\n';
    private File directory = null;
    // Flag to control the uniqueness of the generated facts.
    private boolean uniques;

    private final Map<PythonPredicateFile, Writer> _writers;
    private final EnumMap<PythonPredicateFile, HashSet<String>> _sets;

    public PythonDatabase(File directory, boolean uniques) throws IOException {
        this.directory = directory;
        this.uniques = uniques;
        this._writers = new EnumMap<>(PythonPredicateFile.class);
        this._sets = new EnumMap<>(PythonPredicateFile.class);

        for(PythonPredicateFile predicateFile : EnumSet.allOf(PythonPredicateFile.class)) {
            _writers.put(predicateFile, predicateFile.getWriter(this.directory, ".facts"));
            _sets.put(predicateFile, new HashSet<String>());
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


    private String addColumn(String column) throws IOException {
        // Quote some special characters.
        // TODO: is this worth optimizing?
        String data = column
                .replaceAll("\"", "\\\\\"")
                .replaceAll("\n", "\\\\n")
                .replaceAll("\t", "\\\\t");
        return data;
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
                String s = line.toString();
                if (uniques && !(_sets.get(predicateFile).add(s)))
                    return;
                writer.write(s);
            }
        } catch(IOException exc) {
            throw new RuntimeException(exc);
        }
    }
}