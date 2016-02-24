package doop.soot;

import soot.SootClass;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Driver {
    ThreadFactory _factory;
    boolean _ssa;

    ExecutorService _executor;
    int _classCounter;
    List<SootClass> _sootClasses;
    int _totalClasses;
    int _cores;
    int _classSplit = 3;

    public Driver(ThreadFactory factory, boolean ssa, int totalClasses) {
        _factory = factory;
        _ssa = ssa;
        _classCounter = 0;
        _sootClasses = new ArrayList<>();
        _totalClasses = totalClasses;
        _cores = Runtime.getRuntime().availableProcessors();
        if (_cores > 2) {
            _executor = new ThreadPoolExecutor(_cores/2, _cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        } else {
            _executor = new ThreadPoolExecutor(1, _cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        }
    }

    public void doInParallel(List<SootClass> sootClasses) {
        for(SootClass c : sootClasses) {
            generate(c);
        }
        _executor.shutdown();
        try {
            _executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
        }
    }

    void generate(SootClass _sootClass) {
        _classCounter++;
        _sootClasses.add(_sootClass);

        if ((_classCounter % _classSplit == 0) || (_classCounter + _classSplit-1 >= _totalClasses)) {
            Runnable runnable = _factory.newRunnable(_sootClasses);
            _executor.execute(runnable);
            _sootClasses = new ArrayList<>();
        }
    }
}
