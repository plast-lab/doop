package org.clyze.doop.soot;

import soot.*;
import soot.jimple.*;
import soot.shimple.PhiExpr;
import soot.shimple.Shimple;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Set;

class JimpleCodePrinter implements Runnable {

    private boolean _ssa;
    private boolean _toStdout;
    private String _outputDir;
    private  PrintWriter _writer;
    private final String _suffix;
    private Set<SootClass> _sootClasses;

    JimpleCodePrinter(boolean ssa, boolean toStdout, String outputDir, PrintWriter writer, Set<SootClass> sootClasses) {
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
                    if (FactGenerator.phantomBased(m))
                        continue;

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

                        DoopRenamer.transform(m.getActiveBody());
                    }
                }

                if ( _toStdout ) {
                    synchronized (c.getName().intern()) {
                        Printer.v().printTo(c, _writer);
                        _writer.flush();
                    }
                }
                else {
                    synchronized (c.getName().intern()) {
                        _writer = new PrintWriter(new File(_outputDir, c.getName() + _suffix));
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
