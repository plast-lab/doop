package org.clyze.doop.soot;

import org.clyze.doop.util.filter.ClassFilter;

import java.util.ArrayList;
import java.util.List;

public class SootParameters {
     enum Mode {INPUTS, FULL}
     Mode _mode = null;
     List<String> _inputs = new ArrayList<>();
     List<String> _libraries = new ArrayList<>();
     String _outputDir = null;
     String _main = null;
     boolean _classicFactGen = false;
     boolean _ssa = false;
     boolean _android = false;
     String _androidJars = null;
     boolean _allowPhantom = false;
     boolean _onlyApplicationClassesFactGen = false;
     ClassFilter applicationClassFilter;
     String appRegex = "**";
     boolean _runFlowdroid = false;
     boolean _noFacts = false;

     boolean _generateJimple = false;
     boolean _toStdout = false;
}
