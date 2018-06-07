package org.clyze.doop.soot;

import soot.SootClass;
import soot.SootMethod;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.clyze.doop.soot.DoopErrorCodeException;

public class Driver {
    private ThreadFactory _factory;
    private boolean _generateJimple;
    private int _cores;

    private ExecutorService _executor;
    private int _classCounter;
    private Set<SootClass> _tmpClassGroup;
    private int _totalClasses;
    private int _classSplit = 80;

    Driver(ThreadFactory factory, int totalClasses, boolean generateJimple,
           Integer cores) {
        _factory = factory;
        _classCounter = 0;
        _tmpClassGroup = new HashSet<>();
        _totalClasses = totalClasses;
        _generateJimple = generateJimple;
        _cores = cores == null? Runtime.getRuntime().availableProcessors() : cores;

        System.out.println("Fact generation cores: " + _cores);
    }

    private void initExecutor() {
        if (_cores > 2) {
            _executor = new ThreadPoolExecutor(_cores /2, _cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        } else {
            // No scheduling happens in the case of one core/thread. ("Tasks are
            // guaranteed to execute sequentially, and no more than one task will
            // be active at any given time.")
            _executor = Executors.newSingleThreadExecutor();
        }
    }

    void doInParallel(Set<SootClass> classesToProcess) throws DoopErrorCodeException {
        initExecutor();
        classesToProcess.forEach(this::generate);
        shutdownExecutor();
    }

    void writeInParallel(Set<SootClass> classesToProcess) throws DoopErrorCodeException {
        initExecutor();
        classesToProcess.forEach(this::write);
        shutdownExecutor();
    }

    void shutdownExecutor() throws DoopErrorCodeException {
        _executor.shutdown();
        try {
            _executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
            throw new DoopErrorCodeException(10);
        }
    }

    void doAndroidInSequentialOrder(SootMethod dummyMain, Set<SootClass> sootClasses, FactWriter writer, boolean ssa) {
        FactGenerator factGenerator = new FactGenerator(writer, ssa, sootClasses);
        factGenerator.generate(dummyMain, new Session());
        writer.writeAndroidEntryPoint(dummyMain);
        factGenerator.run();
    }

    private void generate(SootClass curClass) {
        _classCounter++;
        _tmpClassGroup.add(curClass);

        if ((_classCounter % _classSplit == 0) || (_classCounter == _totalClasses)) {
            Runnable runnable = _factory.newFactGenRunnable(_tmpClassGroup);
            _executor.execute(runnable);
            _tmpClassGroup = new HashSet<>();
        }
    }

    private void write(SootClass curClass) {
        _classCounter++;
        _tmpClassGroup.add(curClass);

        if ((_classCounter % _classSplit == 0) || (_classCounter == _totalClasses)) {
            Runnable runnable = _factory.newJimpleGenRunnable(_tmpClassGroup);
            _executor.execute(runnable);
            _tmpClassGroup = new HashSet<>();
        }
    }

}
