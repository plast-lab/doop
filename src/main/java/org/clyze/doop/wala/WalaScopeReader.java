package org.clyze.doop.wala;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.File;

public class WalaScopeReader {
    public static AnalysisScope makeScope(String classPath, File exclusionsFile)
    {
        if (classPath == null) {
            throw new IllegalArgumentException("classPath null");
        }
        AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
        ClassLoaderReference loader = scope.getLoader(AnalysisScope.APPLICATION);

        AnalysisScopeReader.addClassPathToScope(classPath, scope, loader);

        return scope;
    }
}
