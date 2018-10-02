package org.clyze.doop.dex;

import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.common.Parameters;

class DexParameters extends Parameters {
    private boolean printPhantoms;

    public boolean printPhantoms() {
        return printPhantoms;
    }

    @Override
    protected int processNextArg(String[] args, int i) throws DoopErrorCodeException {
        if ("--print-phantoms".equals(args[i])) {
            this.printPhantoms = true;
            return i;
        } else
            return super.processNextArg(args, i);
    }

    @Override
    protected void finishArgProcessing() throws DoopErrorCodeException {
        if (!_android)
            System.err.println("Warning: Android mode is disabled.");
        super.finishArgProcessing();
    }
}
