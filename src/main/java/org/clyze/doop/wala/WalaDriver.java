package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;

public class WalaDriver {

    private WalaThreadFactory _factory;
    private boolean _generateJimple;

    private ExecutorService _executor;
    private int _classCounter;
    private Set<IClass> _tmpClassGroup;
    private int _totalClasses;
    private int _classSplit = 80;

    WalaDriver(WalaThreadFactory factory, int totalClasses, boolean generateJimple,
           Integer cores) {
        _factory = factory;
        _classCounter = 0;
        _tmpClassGroup = new HashSet<>();
        _totalClasses = totalClasses;
        _generateJimple = generateJimple;
        int _cores = cores == null? Runtime.getRuntime().availableProcessors() : cores;

        System.out.println("Fact generation cores: " + _cores);

        if (_cores > 2) {
            _executor = new ThreadPoolExecutor(_cores /2, _cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        } else {
            // No scheduling happens in the case of one core/thread. ("Tasks are
            // guaranteed to execute sequentially, and no more than one task will
            // be active at any given time.")
            _executor = Executors.newSingleThreadExecutor();
        }
    }

    void doSequentially(Iterator<IClass> iClasses, WalaFactWriter writer, String outDir) {
        while (iClasses.hasNext()) {
            _tmpClassGroup.add(iClasses.next());
        }

        WalaFactGenerator factGenerator = new WalaFactGenerator(writer, _tmpClassGroup, outDir);
        //factGenerator.generate(dummyMain, new Session());
        //writer.writeAndroidEntryPoint(dummyMain);
        factGenerator.run();
    }

    void doInParallel(Set<IClass> classesToProcess) {
        classesToProcess.forEach(this::generate);

    }

    private void generate(IClass curClass) {
        _classCounter++;
        _tmpClassGroup.add(curClass);

        if ((_classCounter % _classSplit == 0) || (_classCounter == _totalClasses)) {
            Runnable runnable = _factory.newFactGenRunnable(_tmpClassGroup);
            _executor.execute(runnable);
            _tmpClassGroup = new HashSet<>();
        }
    }

    void shutdown() {
        _executor.shutdown();
        try {
            _executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
        }
    }
}
