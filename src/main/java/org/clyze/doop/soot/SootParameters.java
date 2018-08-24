package org.clyze.doop.soot;

import org.clyze.doop.common.Parameters;
import org.clyze.doop.util.filter.ClassFilter;

import java.util.ArrayList;
import java.util.List;

public class SootParameters extends Parameters {
     enum Mode { INPUTS, FULL }

     Mode _mode = null;
     String _main = null;
     boolean _ssa = false;
     boolean _allowPhantom = false;
     boolean _runFlowdroid = false;
     boolean _generateJimple = false;
     boolean _toStdout = false;
     boolean _ignoreWrongStaticness = false;

     public boolean getRunFlowdroid() {
          return this._runFlowdroid;
     }
}
