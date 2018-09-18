package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import org.clyze.doop.common.Driver;

import java.util.Iterator;

class WalaDriver extends Driver<IClass, WalaThreadFactory> {
    private final boolean _android;
    private final IAnalysisCacheView _cache;

    WalaDriver(WalaThreadFactory factory, int totalClasses,
           Integer cores, boolean android, IAnalysisCacheView cache) {
        super(factory, totalClasses, cores);
        _android = android;
        _cache = cache;
    }

    void generateSequentially(Iterator<IClass> iClasses, WalaFactWriter writer, String outDir) {
        while (iClasses.hasNext()) {
            _tmpClassGroup.add(iClasses.next());
        }

        Runnable factGenerator = new WalaFactGenerator(writer, _tmpClassGroup, outDir, _android, _cache);
        //factGenerator.generate(dummyMain, new Session());
        //writer.writeAndroidEntryPoint(dummyMain);
        factGenerator.run();
    }

    @Override
    protected Runnable getFactGenRunnable() {
        return _factory.newFactGenRunnable(_tmpClassGroup, _cache);
    }

    @Override
    protected Runnable getIRGenRunnable() {
        throw new RuntimeException("Parallel IR generation is not supported.");
    }
}
