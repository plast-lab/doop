package org.clyze.doop.soot;

import soot.SootClass;

import java.io.File;
import java.io.PrintWriter;
import java.util.Set;

public class ThreadFactory {
   private boolean _factGenerationMode;  // used for fact generation, or just Jimple code output?

    FactWriter _factWriter;

    private boolean _ssa;



    private boolean _toStdout;
    private String _outputDir;
    private PrintWriter _printWriter;

    ThreadFactory(FactWriter writer, boolean ssa) {
        _factGenerationMode = true;
        _factWriter = writer;
        _ssa = ssa;
    }

    ThreadFactory(boolean ssa, boolean toStdout, String outputDir) {
        _factGenerationMode = false;
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

    Runnable newRunnable(Set<SootClass> sootClasses) {
        if (_factGenerationMode)
            return new FactGenerator(_factWriter, _ssa, sootClasses);
        else
            return new JimpleCodePrinter(_ssa, _toStdout, _outputDir, _printWriter, sootClasses);
    }

    public FactWriter get_factWriter() {
        return _factWriter;
    }

    public boolean getSsa() {
        return _ssa;
    }

    public boolean inFactGenerationMode() {
        return _factGenerationMode;
    }

    public boolean getToStdout() {
        return _toStdout;
    }

    public String getOutputDir() {
        return _outputDir;
    }

    public PrintWriter getPrintWriter() {
        return _printWriter;
    }


}
