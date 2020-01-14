package org.clyze.doop.python;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.common.Parameters;

class PythonParameters extends Parameters {
    final Collection<String> _appLibraries = new ArrayList<>();
    boolean _generateIR = false;
    boolean _singleFileAnalysis = false;

    @Override
    protected int processNextArg(String[] args, int i) throws DoopErrorCodeException {
        switch (args[i]) {
        case "-l":
            i = shift(args, i);
            _appLibraries.add(args[i]);
            break;
        case "--generate-ir":
            _generateIR = true;
            break;
        case "--single-file-analysis":
            _singleFileAnalysis = true;
            break;
        default:
            return super.processNextArg(args, i);
        }
        return i;
    }
}
