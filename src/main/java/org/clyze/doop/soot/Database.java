package org.clyze.doop.soot;

import org.clyze.doop.common.PredicateFile;

import java.io.Closeable;
import java.io.Flushable;

interface Database extends Closeable, Flushable
{
    void add(PredicateFile predFile, Column arg, Column... args);
    Column addEntity(PredicateFile predFile, String key);
    Column asColumn(String arg);
    Column asIntColumn(String arg);
    Column asEntity(String arg);
    Column asEntity(PredicateFile predFile, String arg);
}
