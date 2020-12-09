package org.clyze.doop.soot;

import org.clyze.doop.common.Driver;
import org.clyze.doop.common.Phantoms;
import soot.SootClass;

class SootDriver extends Driver<SootClass> {
    private final FactWriter factWriter;
    private final SootParameters sootParameters;
    private final Phantoms phantoms;

    SootDriver(int totalClasses, Integer cores, boolean ignoreFactGenErrors,
               FactWriter factWriter, SootParameters sootParameters,
               Phantoms phantoms) {
        super(totalClasses, cores, ignoreFactGenErrors);
        this.factWriter = factWriter;
        this.sootParameters = sootParameters;
        this.phantoms = phantoms;
    }

    @Override
    protected Runnable getFactGenRunnable() {
        return new FactGenerator(factWriter, _tmpClassGroup, this, sootParameters, phantoms);
    }

    @Override
    protected Runnable getIRGenRunnable() {
        return new JimpleGenerator(_tmpClassGroup);
    }
}
