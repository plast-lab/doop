package org.clyze.doop.soot;

import soot.SootClass;

import java.util.Set;

class ThreadFactory {
    final FactWriter _factWriter;

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

    public FactWriter get_factWriter() {
        return _factWriter;
    }

    public boolean getSSA() {
        return _ssa;
    }
}
