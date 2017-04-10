package org.clyze.doop.soot;

import org.clyze.doop.common.PredicateFile;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

class CSVDatabase implements Database
{
    private static final char SEP = '\t';
    private static final char EOL = '\n';

    private File directory;
    private Map<PredicateFile, Writer> writers;

    CSVDatabase(File directory) throws IOException
    {
        this.directory = directory;
        this.writers = new EnumMap<>(PredicateFile.class);

        for(PredicateFile predicateFile : EnumSet.allOf(PredicateFile.class))
            writers.put(predicateFile, predicateFile.getWriter(directory, ".facts"));
    }

    @Override
    public void close() throws IOException
    {
        for(Writer w: writers.values())
            w.close();
    }

    @Override
    public void flush() throws IOException
    {
        for(Writer w: writers.values())
            w.flush();
    }


    private void addColumn(Writer writer, Column column)
        throws IOException
    {
        // Quote some special characters
        String data = column.toString()
            .replaceAll("\"", "\\\\\"")
            .replaceAll("\n", "\\\\n")
            .replaceAll("\t", "\\\\t");

        writer.write(data);
    }


    @Override
    public void add(PredicateFile predicateFile, Column arg, Column ... args)
    {
        try {
            synchronized(predicateFile) {
                Writer writer = getWriter(predicateFile);
                addColumn(writer, arg);
                

                for (Column col : args)
                    addColumn(writer.append(SEP), col);

                writer.write(EOL);
            }
        } catch(IOException exc) {
            throw new RuntimeException(exc);
        }
    }


    private Writer getWriter(PredicateFile predicateFile) throws IOException
    {
        return writers.get(predicateFile);
    }


    @Override
    public Column addEntity(PredicateFile predicateFile, String key)
    {
      add(predicateFile, new Column(key));
      return new Column(key);
    }


    @Override
    public Column asColumn(String arg) {
        return new Column(arg);
    }


    @Override
    public Column asEntity(String arg) {
        return new Column(arg);
    }

    @Override
    public Column asEntity(PredicateFile predicateFile, String arg) {
        return new Column(arg);
    }


    @Override
    public Column asIntColumn(String arg) {
        return new Column(arg);
    }
}
