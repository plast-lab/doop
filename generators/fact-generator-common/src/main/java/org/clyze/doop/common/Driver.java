package org.clyze.doop.common;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A driver for parallel fact generation.
 * @param <C>    class type
 */
public abstract class Driver<C> {
    private ExecutorService _executor;
    private final int _cores;
    protected Set<C> _tmpClassGroup;
    private int _classCounter;
    private final int _totalClasses;
    private final int _classSplit = 80;
    private int errors;

    protected Driver(int totalClasses, Integer cores) {
        this._totalClasses = totalClasses;
        this._cores = cores == null? Runtime.getRuntime().availableProcessors() : cores;
        this._classCounter = 0;
        initTmpClassGroup();

        System.out.println("Fact generation cores: " + _cores);
    }

    protected void initTmpClassGroup() {
        this._tmpClassGroup = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    public synchronized void markError() {
        errors++;
    }

    public synchronized boolean errorsExist() {
        return (errors > 0);
    }

    private void initExecutor() {
        _classCounter = 0;
        initTmpClassGroup();
        errors = 0;

        if (_cores > 2) {
            _executor = new ThreadPoolExecutor(_cores /2, _cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        } else {
            // No scheduling happens in the case of one core/thread. ("Tasks are
            // guaranteed to execute sequentially, and no more than one task will
            // be active at any given time.")
            _executor = Executors.newSingleThreadExecutor();
        }
    }

    private void doInParallel(Iterable<? extends C> classesToProcess, Consumer<? super C> action) throws DoopErrorCodeException {
        initExecutor();
        classesToProcess.forEach(action);
        shutdownExecutor();
    }

    public static void waitForExecutorShutdown(ExecutorService executor) throws DoopErrorCodeException {
        executor.shutdown();
        try {
            boolean res = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            if (!res)
                System.out.println("Executor timeout elapsed.");
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
            throw DoopErrorCodeException.error10();
        }
    }

    private void shutdownExecutor() throws DoopErrorCodeException {
        waitForExecutorShutdown(_executor);
        if (errorsExist()) {
            // Fact-gen errors are always tolerated: warn with the total count of
            // ignored (un-buildable) methods and continue.
            System.err.println("WARNING: fact generation ignored " + errors + " error(s) (un-buildable method bodies); continuing with the facts produced.");
        }
        errors = 0;
    }

    public void generateInParallel(Iterable<? extends C> classesToProcess) throws DoopErrorCodeException {
        doInParallel(classesToProcess, this::generate);
    }

    public void writeInParallel(Iterable<? extends C> classesToProcess) throws DoopErrorCodeException {
        doInParallel(classesToProcess, this::write);
    }

    private void generate(C curClass) {
        _classCounter++;
        _tmpClassGroup.add(curClass);

        if ((_classCounter % _classSplit == 0) || (_classCounter == _totalClasses)) {
            _executor.execute(getFactGenRunnable());
            initTmpClassGroup();
        }
    }

    private void write(C curClass) {
        _classCounter++;
        _tmpClassGroup.add(curClass);

        if ((_classCounter % _classSplit == 0) || (_classCounter == _totalClasses)) {
            _executor.execute(getIRGenRunnable());
            initTmpClassGroup();
        }
    }

    protected abstract Runnable getFactGenRunnable();
    protected abstract Runnable getIRGenRunnable();
}
