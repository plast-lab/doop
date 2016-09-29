package org.clyze.doop.soot;

import soot.Body;
import soot.Printer;
import soot.SootClass;
import soot.SootMethod;
import soot.shimple.Shimple;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;

class FactPrinter implements Runnable {

    private boolean _ssa;
    private boolean _toStdout;
    private String _outputDir;
    private  PrintWriter _writer;
    private final String _suffix;
    private List<SootClass> _sootClasses;

    FactPrinter(boolean ssa, boolean toStdout, String outputDir, PrintWriter writer, List<SootClass> sootClasses) {
        _ssa = ssa;
        _toStdout = toStdout;
        _outputDir = outputDir;
        _writer = writer;
        _suffix = _ssa ? ".shimple" : ".jimple";
        _sootClasses = sootClasses;
    }

    @Override
    public void run() {
        try {
            for (SootClass c : _sootClasses) {
                for (SootMethod m : c.getMethods()) {
                    if (!(m.isAbstract() || m.isNative())) {
                        if (!m.hasActiveBody()) {
							// This instruction is the main bottleneck of. It
							// accounts for more than 80% of its total
							// execution time. However, it is soot internal so
							// we'll need a profiler to optimize it.
                            m.retrieveActiveBody();
                        }

                        if (_ssa) {
                            Body b = m.getActiveBody();
                            b = Shimple.v().newBody(b);
                            m.setActiveBody(b);
                        }
                    }
                }

                if ( _toStdout ) {
                    synchronized (_writer) {
                        Printer.v().printTo(c, _writer);
                        _writer.flush();
                    }
                }
                else {
                    _writer = new PrintWriter(new File(_outputDir, c.getName() + _suffix));
                    synchronized (_writer) {
                        Printer.v().printTo(c, _writer);
                        _writer.close();
                    }
                }

                c.getMethods().forEach(SootMethod::releaseActiveBody);
            }
        } catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
        }
    }
}
