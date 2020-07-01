package org.clyze.doop.soot;

import java.util.HashSet;
import java.util.Set;
import org.clyze.doop.common.Driver;
import org.clyze.doop.common.Phantoms;
import soot.SootClass;
import soot.SootMethod;

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

    void generateMethod(SootMethod dummyMain, FactWriter writer, SootParameters sootParameters) {
        Set<SootClass> sootClasses = new HashSet<>();
        sootClasses.add(dummyMain.getDeclaringClass());
        FactGenerator factGenerator = new FactGenerator(writer, sootClasses, this, sootParameters, phantoms);
        factGenerator.generate(dummyMain, new Session());
        writer.writeAndroidEntryPoint(dummyMain);
        factGenerator.run();
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
