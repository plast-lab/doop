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
    private boolean _ssa;

    private ExecutorService _executor;
    private int _classCounter;
    private Set<SootClass> _tmpClassGroup;
    private int _totalClasses;
    private int _cores;
    private int _classSplit = 5;

    Driver(ThreadFactory factory, boolean ssa, int totalClasses) {
        _factory = factory;
        _ssa = ssa;
        _classCounter = 0;
        _tmpClassGroup = new HashSet<>();
        _totalClasses = totalClasses;
        _cores = Runtime.getRuntime().availableProcessors();
        if (_cores > 2) {
            _executor = new ThreadPoolExecutor(_cores/2, _cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        } else {
            _executor = new ThreadPoolExecutor(1, _cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        }
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
        if(_factory.inFactGenerationMode()) {
            FactGenerator factGenerator = new FactGenerator(_factory.get_factWriter(), _factory.getSsa(), sootClasses);
            factGenerator.run();
        }
        else {
            JimpleCodePrinter jimpleCodePrinter = new JimpleCodePrinter(_ssa, _factory.getToStdout(), _factory.getOutputDir(), _factory.getPrintWriter(), sootClasses); //.stream().collect(Collectors.toList()));
            jimpleCodePrinter.run();
        }
    }

//////     Old handling, using FlowDroid-generated main method
//    void doAndroidInSequentialOrder(SootMethod dummyMain, Set<SootClass> sootClasses, FactWriter writer, boolean ssa) {
//        FactGenerator factGenerator = new FactGenerator(writer, ssa, sootClasses);
//        factGenerator.generate(dummyMain, new Session());
//        writer.writeAndroidEntryPoint(dummyMain);
//        factGenerator.run();
//    }

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
