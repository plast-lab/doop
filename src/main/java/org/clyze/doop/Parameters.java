package org.clyze.doop;

import java.util.ArrayList;
import java.util.List;
import org.clyze.doop.util.filter.ClassFilter;

/**
 * This class contains common parameters for Doop Java front-ends.
 */
public abstract class Parameters {
    protected List<String> _inputs = new ArrayList<>();
    protected String _outputDir = null;
    public String _extraSensitiveControls = "";
    public ClassFilter applicationClassFilter;
    public boolean _android = false;
    public Integer _cores = null;
    public String _androidJars = null;

    public void setInputs(List<String> inputs) {
        this._inputs = inputs;
    }

    public List<String> getInputs() {
        return this._inputs;
    }

    public void setOutputDir(String outputDir) {
        this._outputDir = outputDir;
    }

    public String getOutputDir() {
        return _outputDir;
    }
}
