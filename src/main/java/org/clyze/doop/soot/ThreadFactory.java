package org.clyze.doop.soot;

import soot.SootClass;

import java.io.File;
import java.io.PrintWriter;
import java.util.Set;

public class ThreadFactory {
    FactWriter _factWriter;

    private boolean _ssa;
    private boolean _generateJimple;

    ThreadFactory(FactWriter factWriter, boolean ssa, boolean generateJimple) {
        _factWriter = factWriter;
        _ssa = ssa;
        _generateJimple = generateJimple;
    }

    Runnable newRunnable(Set<SootClass> sootClasses) {
        return new FactGenerator(_factWriter, _ssa, sootClasses, _generateJimple);
    }

    public FactWriter get_factWriter() {
        return _factWriter;
    }

    public boolean getSSA() {
        return _ssa;
    }
}
