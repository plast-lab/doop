package org.clyze.doop.python;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class PythonParameters {
        List<String> _inputs = new ArrayList<>();
        Collection<String> _appLibraries = new ArrayList<>();
        String _outputDir = null;
        Integer _cores = null;
        boolean _generateIR = false;
        boolean _singleFileAnalysis = false;
}
