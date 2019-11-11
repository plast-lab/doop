package org.clyze.doop.python;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class PythonParameters {
        final List<String> _inputs = new ArrayList<>();
        final Collection<String> _appLibraries = new ArrayList<>();
        String _outputDir = null;
        Integer _cores = null;
        boolean _generateIR = false;
        boolean _singleFileAnalysis = false;
}
