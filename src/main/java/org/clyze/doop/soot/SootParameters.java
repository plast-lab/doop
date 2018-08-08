package org.clyze.doop.soot;

import org.clyze.doop.Parameters;
import org.clyze.doop.util.filter.ClassFilter;

import java.util.ArrayList;
import java.util.List;

public class SootParameters extends Parameters {
     enum Mode { INPUTS, FULL }

     public enum FactsSubSet { APP, APP_N_DEPS, PLATFORM }

     Mode _mode = null;
     List<String> _libraries = new ArrayList<>();
     List<String> _dependencies = new ArrayList<>();
     String _main = null;
     boolean _ssa = false;
     boolean _allowPhantom = false;
     FactsSubSet _factsSubSet = null;
     String appRegex = "**";
     boolean _runFlowdroid = false;
     boolean _noFacts = false;
     String _rOutDir = null;
     boolean _generateJimple = false;
     boolean _toStdout = false;
     boolean _ignoreWrongStaticness = false;
     String _seed = null;
     String _specialCSMethods = null;

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
