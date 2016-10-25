package org.clyze.doop.soot;

import soot.SootClass;
import soot.SootMethod;

import java.util.ArrayList;
import java.util.List;
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
    private List<SootClass> _sootClasses;
    private int _totalClasses;
    private int _cores;
    private int _classSplit = 3;

    Driver(ThreadFactory factory, boolean ssa, int totalClasses) {
        _factory = factory;
        _ssa = ssa;
        _classCounter = 0;
        _sootClasses = new ArrayList<>();
        _totalClasses = totalClasses;
        _cores = Runtime.getRuntime().availableProcessors();
        if (_cores > 2) {
            _executor = new ThreadPoolExecutor(_cores/2, _cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        } else {
            _executor = new ThreadPoolExecutor(1, _cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        }
    }

    void doInParallel(Set<SootClass> sootClasses) {
        sootClasses.forEach(this::generate);
        _executor.shutdown();
        try {
            _executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
        }
    }

    static void doInSequentialOrder(Set<SootClass> sootClasses, FactWriter writer, boolean ssa) {
        SequentialFactGenerator sequentialFactGenerator = new SequentialFactGenerator(writer, ssa);
        sootClasses.forEach(sequentialFactGenerator::generate);
    }

    static void doInSequentialOrder(SootMethod dummyMain, Set<SootClass> sootClasses, FactWriter writer, boolean ssa) {
        SequentialFactGenerator sequentialFactGenerator = new SequentialFactGenerator(writer, ssa);
        sequentialFactGenerator.generate(dummyMain, new Session());
        writer.writeAndroidEntryPoint(dummyMain);
        sootClasses.forEach(sequentialFactGenerator::generate);
    }

    void generateDummyMainMethod(SootMethod dummyMain) {
        SequentialFactGenerator sequentialFactGenerator = new SequentialFactGenerator(_factory._factWriter, _ssa);
        sequentialFactGenerator.generate(dummyMain, new Session());
    }

    private void generate(SootClass _sootClass) {
        _classCounter++;
        _sootClasses.add(_sootClass);

        if ((_classCounter % _classSplit == 0) || (_classCounter + 1 == _totalClasses)) {
            Runnable runnable = _factory.newRunnable(_sootClasses);
            _executor.execute(runnable);
            _sootClasses = new ArrayList<>();
        }
    }
}
