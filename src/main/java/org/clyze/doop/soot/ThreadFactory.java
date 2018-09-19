package org.clyze.doop.soot;

import soot.SootClass;

import java.util.Set;

class ThreadFactory {
    private final FactWriter _factWriter;
    private final boolean _ssa;
    private final boolean _reportPhantoms;

    ThreadFactory(FactWriter factWriter, boolean ssa, boolean reportPhantoms) {
        _factWriter = factWriter;
        _ssa = ssa;
        _reportPhantoms = reportPhantoms;
    }

    Runnable newFactGenRunnable(Set<SootClass> sootClasses) {
        return new FactGenerator(_factWriter, _ssa, sootClasses, _reportPhantoms);
    }

    Runnable newJimpleGenRunnable(Set<SootClass> sootClasses) {
        return new JimpleGenerator(sootClasses);
    }

}
