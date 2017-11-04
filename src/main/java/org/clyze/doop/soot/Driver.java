package org.clyze.doop.soot;

import soot.SootClass;
import soot.SootMethod;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class Driver {
    private ThreadFactory _factory;
    private boolean _generateJimple;

    private ExecutorService _executor;
    private int _classCounter;
    private Set<SootClass> _tmpClassGroup;
    private int _totalClasses;
    private int _cores;
    private int _classSplit = 80;

    Driver(ThreadFactory factory, int totalClasses, boolean generateJimple) {
        _factory = factory;
        _classCounter = 0;
        _tmpClassGroup = new HashSet<>();
        _totalClasses = totalClasses;
        _generateJimple = generateJimple;
        _cores = readCores();

        if (_cores > 2) {
            _executor = new ThreadPoolExecutor(_cores/2, _cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        } else {
            _executor = new ThreadPoolExecutor(1, _cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        }
    }

    // Read number of cores from environment variable. If the variable
    // doesn't exist or its value is invalid, use all processors.
    private static int readCores() {
        String coresEnvVar = "DOOP_FACTGEN_CORES";
        String coresEnvVal = System.getenv(coresEnvVar);
        if (coresEnvVal != null) {
            try {
                int c = Integer.parseInt(coresEnvVal);
                System.out.println("Using " + coresEnvVar + " = " + coresEnvVal);
                return c;
            } catch (NumberFormatException nfe) {
                System.out.println("Invalid " + coresEnvVar + " = " + coresEnvVal);
            }
        }
        return Runtime.getRuntime().availableProcessors();
    }

    void doInParallel(Set<SootClass> classesToProcess) {

        classesToProcess.forEach(this::generate);
        _executor.shutdown();
        try {
            _executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
        }
    }

    void doInSequentialOrder(Set<SootClass> sootClasses) {
        FactGenerator factGenerator = new FactGenerator(_factory.get_factWriter(), _factory.getSSA(), sootClasses, _generateJimple);
        factGenerator.run();
    }

    void doAndroidInSequentialOrder(SootMethod dummyMain, Set<SootClass> sootClasses, FactWriter writer, boolean ssa) {
        FactGenerator factGenerator = new FactGenerator(writer, ssa, sootClasses, _generateJimple);
        factGenerator.generate(dummyMain, new Session());
        writer.writeAndroidEntryPoint(dummyMain);
        factGenerator.run();
    }

    private void generate(SootClass curClass) {
        _classCounter++;
        _tmpClassGroup.add(curClass);

        if ((_classCounter % _classSplit == 0) || (_classCounter == _totalClasses)) {
            Runnable runnable = _factory.newRunnable(_tmpClassGroup);
            _executor.execute(runnable);
            _tmpClassGroup = new HashSet<>();
        }
    }
}
