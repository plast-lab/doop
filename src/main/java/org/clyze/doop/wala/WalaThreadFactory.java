package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import soot.SootClass;

import java.util.Iterator;
import java.util.Set;

public class WalaThreadFactory {
    WalaFactWriter _factWriter;
    String _outDir;
    boolean _android;

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