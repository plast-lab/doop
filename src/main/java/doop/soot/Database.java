package doop.soot;

import java.io.IOException;
import java.io.Closeable;
import java.io.Flushable;

import doop.PredicateFile;

public interface Database extends Closeable, Flushable
{
    public void add(PredicateFile predFile, Column arg, Column... args);
    public Column addEntity(PredicateFile predFile, String key);
    public Column asColumn(String arg);
    public Column asIntColumn(String arg);
    public Column asEntity(String arg);
    public Column asEntity(PredicateFile predFile, String arg);
}
