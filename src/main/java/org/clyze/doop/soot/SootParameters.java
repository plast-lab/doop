package org.clyze.doop.soot;

import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.common.Parameters;

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

    @Override
    public int processNextArg(String[] args, int i) throws DoopErrorCodeException {
        int next_i = super.processNextArg(args, i);
        if (next_i != -1)
            return next_i;

        switch (args[i]) {
        case "--full":
            if (this._mode != null) {
                System.err.println("error: duplicate mode argument");
                throw new DoopErrorCodeException(1);
            }
            this._mode = SootParameters.Mode.FULL;
            break;
        case "--main":
            i = shift(args, i);
            this._main = args[i];
            break;
        case "--ssa":
            this._ssa = true;
            break;
        case "--allow-phantom":
            this._allowPhantom = true;
            break;
        case "--run-flowdroid":
            this._runFlowdroid = true;
            break;
        case "--generate-jimple":
            this._generateJimple = true;
            break;
        case "--stdout":
            this._toStdout = true;
            break;
        case "--ignoreWrongStaticness":
            this._ignoreWrongStaticness = true;
            break;
        default:
            return -1;
        }
        return i;
    }

}
