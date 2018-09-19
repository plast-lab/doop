package org.clyze.doop.wala;

import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.common.Parameters;

class WalaParameters extends Parameters {
    String _javaPath = null;

    WalaParameters(String[] args) throws DoopErrorCodeException {
        super(args);
    }

    @Override
    public int processNextArg(String[] args, int i) throws DoopErrorCodeException {
        if ("-p".equals(args[i])) {
            int next_i = shift(args, i);
            this._javaPath = args[next_i];
            return next_i;
        } else
            return super.processNextArg(args, i);
    }
}
