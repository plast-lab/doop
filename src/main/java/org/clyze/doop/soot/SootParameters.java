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
     boolean _ssa = false;
     boolean _android = false;
     String _androidJars = null;
     boolean _allowPhantom = false;
     boolean _onlyApplicationClassesFactGen = false;
     ClassFilter applicationClassFilter;
     String appRegex = "**";
     boolean _runFlowdroid = false;
     boolean _noFacts = false;
     boolean _uniqueFacts = false;
     String _rOutDir = null;
     boolean _generateJimple = false;
     boolean _toStdout = false;
     Integer _cores = null;

     public void setInputs(List<String> inputs) {
          this._inputs = inputs;
     }

     public List<String> getInputs() {
          return this._inputs;
     }

     public void setLibraries(List<String> libraries) {
          this._libraries = libraries;
     }

     public List<String> getLibraries() {
          return this._libraries;
     }

     public List<String> getInputsAndLibraries() {
          List<String> ret = new ArrayList<>();
          ret.addAll(this._inputs);
          ret.addAll(this._libraries);
          return ret;
     }

     public boolean getRunFlowdroid() {
          return this._runFlowdroid;
     }
}
