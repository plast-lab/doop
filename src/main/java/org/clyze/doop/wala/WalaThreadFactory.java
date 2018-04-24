package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;
import soot.SootClass;

import java.util.Iterator;
import java.util.Set;

public class WalaThreadFactory {
    WalaFactWriter _factWriter;
    String _outDir;

    WalaThreadFactory(WalaFactWriter factWriter, String outDir) {
        _factWriter = factWriter;
        _outDir = outDir;
    }

    Runnable newFactGenRunnable(Set<IClass> iClasses) {
        return new WalaFactGenerator(_factWriter, iClasses, _outDir);
    }

    public WalaFactWriter get_factWriter() {
        return _factWriter;
    }
}