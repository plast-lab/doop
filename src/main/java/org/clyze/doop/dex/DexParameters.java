package org.clyze.doop.dex;

import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.common.Parameters;

class DexParameters extends Parameters {
    @Override
    protected void finishArgProcessing() throws DoopErrorCodeException {
        if (!_android)
            System.err.println("WARNING: Android mode is disabled.");
        super.finishArgProcessing();
    }
}
