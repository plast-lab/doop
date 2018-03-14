package org.clyze.doop.wala;

import java.util.ArrayList;
import java.util.List;

public class WalaParameters {
    List<String> _inputs = new ArrayList<>();
    List<String> _libraries = new ArrayList<>();
    String _outputDir = null;

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

}
