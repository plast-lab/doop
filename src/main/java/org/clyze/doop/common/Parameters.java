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
    public FactsSubSet _factsSubSet = null;
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

    public static int shift(String[] args, int index) throws DoopErrorCodeException {
        if(args.length == index + 1) {
            System.err.println("error: option " + args[index] + " requires an argument");
            throw new DoopErrorCodeException(9);
        }
        return index + 1;
    }

    /**
     * Process next command line argument and update parameters.
     *
     * @param args        the command line arguments
     * @param i           the index of the next argument to read
     *
     * @return  -1 if the next argument was not recognized, otherwise
     *          the index of the last argument processed
     */
    public int processNextArg(String[] args, int i) throws DoopErrorCodeException {
        switch (args[i]) {
        case "--android-jars":
            i = shift(args, i);
            this._android = true;
            this._androidJars = args[i];
            break;
        case "-i":
            i = shift(args, i);
            this.getInputs().add(args[i]);
            break;
        case "-d":
            i = shift(args, i);
            this.setOutputDir(args[i]);
            break;
        case "--application-regex":
            i = shift(args, i);
            this.setAppRegex(args[i]);
            break;
        case "--fact-gen-cores":
            i = shift(args, i);
            try {
                this._cores = new Integer(args[i]);
            } catch (NumberFormatException nfe) {
                System.out.println("Invalid cores argument: " + args[i]);
            }
            break;
        case "--facts-subset":
            i = shift(args, i);
            this._factsSubSet = Parameters.FactsSubSet.valueOf(args[i]);
            break;
        case "--R-out-dir":
            i = shift(args, i);
            this._rOutDir = args[i];
            break;
        case "--extra-sensitive-controls":
            i = shift(args, i);
            this.setExtraSensitiveControls(args[i]);
            break;
        case "--seed":
            i = shift(args, i);
            this._seed = args[i];
            break;
        case "--special-cs-methods":
            i = shift(args, i);
            this._specialCSMethods = args[i];
            break;
        default:
            return -1;
        }
        return i;
    }

}
