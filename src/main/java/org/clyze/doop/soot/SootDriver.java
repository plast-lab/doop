package org.clyze.doop.soot;

import java.util.Set;
import org.clyze.doop.common.Driver;
import soot.SootClass;
import soot.SootMethod;

class SootDriver extends Driver<SootClass, ThreadFactory> {

    SootDriver(ThreadFactory factory, int totalClasses, Integer cores) {
        super(factory, totalClasses, cores);
    }

    void doAndroidInSequentialOrder(SootMethod dummyMain, Set<SootClass> sootClasses, FactWriter writer, boolean ssa) {
        FactGenerator factGenerator = new FactGenerator(writer, ssa, sootClasses);
        factGenerator.generate(dummyMain, new Session());
        writer.writeAndroidEntryPoint(dummyMain);
        factGenerator.run();
    }

    @Override
    protected Runnable getFactGenRunnable() {
        return _factory.newFactGenRunnable(_tmpClassGroup);
    }

    @Override
    protected Runnable getIRGenRunnable() {
        return _factory.newJimpleGenRunnable(_tmpClassGroup);
    }
}
