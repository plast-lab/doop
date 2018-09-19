package org.clyze.doop.soot;

import soot.SootClass;

import java.util.Set;

class ThreadFactory {
    private final FactWriter _factWriter;

    private final boolean _ssa;

    ThreadFactory(FactWriter factWriter, boolean ssa) {
        _factWriter = factWriter;
        _ssa = ssa;
    }

    Runnable newFactGenRunnable(Set<SootClass> sootClasses) {
        return new FactGenerator(_factWriter, _ssa, sootClasses);
    }

    Runnable newJimpleGenRunnable(Set<SootClass> sootClasses) {
        return new JimpleGenerator(sootClasses);
    }

}
