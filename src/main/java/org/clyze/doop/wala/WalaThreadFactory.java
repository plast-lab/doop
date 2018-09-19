package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;

import java.util.Set;

class WalaThreadFactory {
    private final WalaFactWriter _factWriter;
    private final String _outDir;
    private final boolean _android;

    WalaThreadFactory(WalaFactWriter factWriter, String outDir, boolean isAndroidAnalysis) {
        _factWriter = factWriter;
        _outDir = outDir;
        _android = isAndroidAnalysis;
    }

    Runnable newFactGenRunnable(Set<IClass> iClasses, IAnalysisCacheView cache) {
        return new WalaFactGenerator(_factWriter, iClasses, _outDir, _android, cache);
    }

    public WalaFactWriter get_factWriter() {
        return _factWriter;
    }
}
