package doop.soot;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.EnumMap;
import java.util.Map;

public class TRAPDatabase implements Database {
    private final char SEP = ',';
    private final char EOL = '\n';

    private File _directory;
    private Map<PredicateFile, Writer> _writers;

    public TRAPDatabase(File directory)
    {
        super();
        _directory = directory;
        _writers = new EnumMap<>(PredicateFile.class);
    }

    @Override
    public void close() throws IOException
    {
        for(Writer w: _writers.values())
        {
            w.close();
        }
    }

    @Override
    public void flush() throws IOException
    {
        for(Writer w: _writers.values())
        {
            w.flush();
        }
    }

    @Override
    public void add(PredicateFile predicateFile, Column arg, Column... args)
    {
        try
        {
            Writer w = getWriter(predicateFile);
            writePredicate(predicateFile.toString(), w);
            w.write("(" + arg);

            for (Column c : args)
                w.write(SEP + "" + c);

            w.write(")" + EOL);
        }
        catch(IOException exc)
        {
            throw new RuntimeException(exc);
        }
    }

    private Writer getWriter(PredicateFile predicateFile) throws IOException
    {
        Writer result = _writers.get(predicateFile);

        if(result == null)
        {
            result = predicateFile.getWriter(_directory, ".trap");
            _writers.put(predicateFile, result);
        }

        return result;
    }


    private String[] entities = new String[20];
    int index = 0;

    @Override
    public Column addEntity(PredicateFile predicateFile, String id) {
        try
        {
            String predicateName = predicateFile.toString();
            String escapedID = escape(id);
            String key = createKey(escapedID);

            String check = key + predicateName;
            for(int i = 0; i < 20; i++) {
                if(check.equals(entities[(index - 1 - i + 20) % 20]))
                    return new Column(key);
            }
            entities[index] = check;
            index = (index + 1) % 20;

            Writer w = getWriter(predicateFile);
            writePredicate(predicateName, w);
            w.write("(");
            w.write(key);
            w.write(SEP);
            w.write(escapedID);
            w.write(")");
            w.write(EOL);
            return new Column(key);
        }
        catch(IOException exc) {
            throw new RuntimeException(exc);
        }
    }

    @Override
    public Column asColumn(String arg) {
        return new Column(escape(arg));
    }

    @Override
    public Column asIntColumn(String arg) {
        return new Column(arg);
    }

    private String escape(String id) {
        id = id.replaceAll("\"", "\"\"");
        return "\"" + id + "\"";
    }

    private void writePredicate(String predicateName, Writer w) throws IOException
    {
        predicateName = predicateName.replaceAll("-", "");
        w.write(Character.toLowerCase(predicateName.charAt(0)));
        w.write(predicateName.substring(1));
    }

    @Override
    public Column asEntity(String id) {
        return new Column(createKey(escape(id)));
    }

    @Override
    public Column asEntity(PredicateFile predicateFile, String id) {
        return new Column(createKey(escape(id)));
    }

    private String createKey(String escapedID) {
        escapedID = escapedID.replace('{', '_');
        escapedID = escapedID.replace('}', '_');
        return "@" + escapedID;
    }
}
