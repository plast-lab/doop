package org.clyze.doop.python.utils;

import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;

public class PythonIRPrinter {
    private IAnalysisCacheView _cache;
    private String _outputDir;

    public PythonIRPrinter(IAnalysisCacheView cache, String outputDir)
    {
        _outputDir = outputDir;
        _cache = cache;
    }
}
