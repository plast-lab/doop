package org.clyze.doop.python;


import java.util.ArrayList;
import java.util.List;

public class PythonParameters {
        List<String> _inputs = new ArrayList<>();
        List<String> _appLibraries = new ArrayList<>();
        String _outputDir = null;
        Integer _cores = null;
        boolean _uniqueFacts = false;
        boolean _generateIR = false;
}
