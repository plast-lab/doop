package org.clyze.doop.dex;

import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.common.Parameters;

class DexParameters extends Parameters {
    private boolean printPhantoms;
    private String decompressDir;

    public DexParameters(String[] args) throws DoopErrorCodeException {
        super(args);
    }

    public boolean printPhantoms() {
        return printPhantoms;
    }

    @Override
    public int processNextArg(String[] args, int i) throws DoopErrorCodeException {
        if ("--print-phantoms".equals(args[i])) {
            this.printPhantoms = true;
            return i;
        } else if ("--apk-decompress-dir".equals(args[i])) {
            int next_i = shift(args, i);
            this.decompressDir = args[next_i];
            return next_i;
        } else
            return super.processNextArg(args, i);
    }

    public String getDecompressDir() {
        return decompressDir;
    }
}
