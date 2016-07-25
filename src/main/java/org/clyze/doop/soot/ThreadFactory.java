package org.clyze.doop.soot;

import soot.SootClass;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

public class ThreadFactory {

    private boolean _makeClassGenerator;

    FactWriter _factWriter;

    private boolean _ssa;
    private boolean _toStdout;
    private String _outputDir;
    private PrintWriter _printWriter;

    ThreadFactory(FactWriter writer, boolean ssa) {
        _makeClassGenerator = true;
        _factWriter = writer;
        _ssa = ssa;
    }

    ThreadFactory(boolean ssa, boolean toStdout, String outputDir) {
        _makeClassGenerator = false;
        _ssa = ssa;
        _toStdout = toStdout;
        _outputDir = outputDir;
        if (_toStdout) {
            _printWriter = new PrintWriter(System.out);
        } else {
            new File(_outputDir).mkdirs();
            _printWriter = null;
        }
    }

    Runnable newRunnable(List<SootClass> sootClasses) {
        if (_makeClassGenerator)
            return new RunnableFactGenerator(_factWriter, _ssa, sootClasses);
        else
            return new FactPrinter(_ssa, _toStdout, _outputDir, _printWriter, sootClasses);
    }
}
