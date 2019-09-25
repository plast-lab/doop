package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import org.clyze.doop.common.Driver;

class WalaDriver extends Driver<IClass> {
    private final IAnalysisCacheView _cache;
    private final WalaFactWriter _factWriter;
    private final String _outDir;
    private final boolean _android;

    WalaDriver(int totalClasses, Integer cores, IAnalysisCacheView cache,
               boolean ignoreFactGenErrors, WalaFactWriter factWriter,
               String outDir, boolean isAndroidAnalysis) {
        super(totalClasses, cores, ignoreFactGenErrors);
        _cache = cache;
        _factWriter = factWriter;
        _outDir = outDir;
        _android = isAndroidAnalysis;
    }

    @Override
    protected Runnable getFactGenRunnable() {
        return new WalaFactGenerator(_factWriter, _tmpClassGroup, _outDir, _android, _cache);
    }

    @Override
    protected Runnable getIRGenRunnable() {
        throw new RuntimeException("Parallel IR generation is not supported.");
    }
}
