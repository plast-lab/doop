package org.clyze.doop.soot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.common.Driver;
import org.clyze.doop.common.Phantoms;
import soot.SootClass;
import soot.SootMethod;

class SootDriver extends Driver<SootClass> {
    private final FactWriter factWriter;
    private final SootParameters sootParameters;
    private final Phantoms phantoms;
    private final ClassHeapFinder classHeaps = new ClassHeapFinder();
    private FactGenerator.Pass pass;

    SootDriver(int totalClasses, Integer cores,
               FactWriter factWriter, SootParameters sootParameters,
               Phantoms phantoms) {
        super(totalClasses, cores);
        this.factWriter = factWriter;
        this.sootParameters = sootParameters;
        this.phantoms = phantoms;
    }

    /**
     * Generates facts in two passes over the classes. Pass 1 builds each
     * method body, writes its instruction facts and releases it. Pass 2
     * writes class, field and method declarations; since it starts after
     * every body is built, it sees all members that body construction added
     * to the Scene (phantom methods and fields), independently of timing.
     */
    void generateFacts(Iterable<SootClass> classes) throws DoopErrorCodeException {
        pass = FactGenerator.Pass.BODIES;
        generateInParallel(classes);
        catchUpBodies(classes);
        pass = FactGenerator.Pass.DECLARATIONS;
        generateInParallel(classes);
    }

    private final Set<SootMethod> bodiesClaimed = ConcurrentHashMap.newKeySet();

    /** True for the first caller only: each body is processed once. */
    boolean claimBody(SootMethod m) {
        return bodiesClaimed.add(m);
    }

    /**
     * Body construction can add methods with bodies to classes that pass 1
     * may already have iterated (Soot adds a method that throws
     * NoSuchMethodError when a call targets a method that does not exist).
     * Whether pass 1 saw them depends on thread timing, so process every
     * method still missing here, on one thread, until none is left.
     */
    private void catchUpBodies(Iterable<SootClass> classes) {
        FactGenerator gen = new FactGenerator(factWriter, Collections.emptySet(), this, sootParameters, phantoms,
                                              FactGenerator.Pass.BODIES, classHeaps);
        int total = 0;
        while (true) {
            List<SootMethod> missing = new ArrayList<>();
            for (SootClass c : classes)
                for (SootMethod m : new ArrayList<>(c.getMethods()))
                    if (!(m.isPhantom() || m.isAbstract() || m.isNative()) && !bodiesClaimed.contains(m))
                        missing.add(m);
            if (missing.isEmpty())
                break;
            total += missing.size();
            missing.forEach(gen::generateBodyOf);
        }
        System.out.println("Bodies of methods added during body construction: " + total);
    }

    /** Class-constant types of all bodies, collected in pass 1. */
    ClassHeapFinder classHeaps() {
        return classHeaps;
    }

    @Override
    protected Runnable getFactGenRunnable() {
        return new FactGenerator(factWriter, _tmpClassGroup, this, sootParameters, phantoms, pass, classHeaps);
    }

    @Override
    protected Runnable getIRGenRunnable() {
        return new JimpleGenerator(_tmpClassGroup);
    }
}
