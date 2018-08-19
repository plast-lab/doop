package org.clyze.doop.wala;

import org.clyze.doop.common.Parameters;
import org.clyze.doop.util.filter.ClassFilter;

import java.util.ArrayList;
import java.util.List;

public class WalaParameters extends Parameters {
    List<String> _platformLibraries = new ArrayList<>();
    String _javaPath = null;
    boolean _generateIR = false;

    public void setPlatformLibraries(List<String> libraries) {
        this._platformLibraries = libraries;
    }

    public List<String> getPlatformLibraries() {
        return this._platformLibraries;
    }

    @Override
    public List<String> getInputsAndLibraries() {
        List<String> ret = new ArrayList<>();
        ret.addAll(this._inputs);
        ret.addAll(this._libraries);
        ret.addAll(this._platformLibraries);
        return ret;
    }

}
