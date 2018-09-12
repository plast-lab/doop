package org.clyze.doop.soot;

import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.common.Parameters;

public class SootParameters extends Parameters {
     enum Mode { INPUTS, FULL }

     Mode _mode = null;
     String _main = null;
     boolean _ssa = false;
     boolean _allowPhantom = false;
     private boolean _runFlowdroid = false;
     boolean _generateJimple = false;
     private boolean _toStdout = false;
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

    /**
     * Finishes command-line argument processing (e.g., checks for incompatible
     * or missing options or sets defaults).
     * @throws DoopErrorCodeException    exception containing error code
     */
    public void finishArgProcessing() throws DoopErrorCodeException {
        super.finishArgProcessing();

        if (_mode == null)
            _mode = SootParameters.Mode.INPUTS;

        if (_toStdout && !_generateJimple) {
            System.err.println("error: --stdout must be used with --generate-jimple");
            throw new DoopErrorCodeException(7);
        }
        else if (_toStdout && getOutputDir() != null) {
            System.err.println("error: --stdout and -d options are not compatible");
            throw new DoopErrorCodeException(2);
        }
        else if ((getInputs().stream().anyMatch(s -> s.endsWith(".apk") || s.endsWith(".aar"))) &&
                (!_android)) {
            System.err.println("error: the --platform parameter is mandatory for .apk/.aar inputs, run './doop --help' to see the valid Android platform values");
            throw new DoopErrorCodeException(3);
        }

        if (!_toStdout && getOutputDir() == null)
            setOutputDir(System.getProperty("user.dir"));
    }

}
