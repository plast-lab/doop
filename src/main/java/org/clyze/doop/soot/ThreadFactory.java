package org.clyze.doop.soot;

import java.util.Set;
import org.clyze.doop.common.Driver;
import soot.SootClass;

class ThreadFactory {
    private final FactWriter factWriter;
    private Driver driver;
    private final SootParameters sootParameters;

    ThreadFactory(FactWriter factWriter, SootParameters sootParameters) {
        this.factWriter = factWriter;
        this.sootParameters = sootParameters;
    }

    void setDriver(Driver driver) {
        this.driver = driver;
    }

    Runnable newFactGenRunnable(Set<SootClass> sootClasses) {
        return new FactGenerator(factWriter, sootClasses, driver, sootParameters);
    }

    Runnable newJimpleGenRunnable(Set<SootClass> sootClasses) {
        return new JimpleGenerator(sootClasses);
    }

}
