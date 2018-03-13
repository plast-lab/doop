package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;
import org.clyze.doop.soot.JimpleGenerator;
import soot.SootClass;

import java.util.Set;

public class WalaThreadFactory {
    WalaFactWriter _factWriter;

    private boolean _ssa;

    WalaThreadFactory(WalaFactWriter factWriter) {
        _factWriter = factWriter;
    }

    Runnable newFactGenRunnable(Set<IClass> iClasses) {
        return new WalaFactGenerator(_factWriter, iClasses);
    }

    Runnable newJimpleGenRunnable(Set<SootClass> sootClasses) {
        return new JimpleGenerator(sootClasses);
    }

    public WalaFactWriter get_factWriter() {
        return _factWriter;
    }

    public boolean getSSA() {
        return _ssa;
    }
}
