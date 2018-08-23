package org.clyze.doop.common;

import java.util.ArrayList;
import java.util.List;
import org.clyze.doop.util.filter.ClassFilter;
import org.clyze.doop.util.filter.GlobClassFilter;

/**
 * This class contains common parameters for Doop Java front-ends.
 */
public abstract class Parameters {
    protected List<String> _inputs = new ArrayList<>();
    protected List<String> _libraries = new ArrayList<>();
    private List<String> _dependencies = new ArrayList<>();
    protected String _outputDir = null;
    private String _extraSensitiveControls = "";
    private String appRegex;
    public ClassFilter applicationClassFilter;
    public boolean _android = false;
    public Integer _cores = null;
    public String _androidJars = null;
    public String _seed = null;
    public String _specialCSMethods = null;
    public String _rOutDir = null;
    private boolean _noFacts = false;

    public enum FactsSubSet { APP, APP_N_DEPS, PLATFORM }

    public Parameters() {
	setAppRegex("**");
    }

    public void setAppRegex(String regex) {
        this.appRegex = regex;
        this.applicationClassFilter = new GlobClassFilter(this.appRegex);
    }

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

    public void setOutputDir(String outputDir) {
        this._outputDir = outputDir;
    }

    public String getOutputDir() {
        return _outputDir;
    }

    public boolean isApplicationClass(String className) {
        return applicationClassFilter.matches(className);
    }

    public List<String> getInputsAndLibraries() {
        List<String> ret = new ArrayList<>();
        ret.addAll(this._inputs);
        ret.addAll(this._libraries);
        return ret;
    }

    public void setNoFacts(boolean b) {
        this._noFacts = b;
    }

    public boolean noFacts() {
        return _noFacts;
    }

    public void setExtraSensitiveControls(String s) {
        this._extraSensitiveControls = s;
    }

    public String getExtraSensitiveControls() {
        return _extraSensitiveControls;
    }

    public List<String> getDependencies() {
        return _dependencies;
    }

    public void setDependencies(List<String> deps) {
        this._dependencies = deps;
    }


}
