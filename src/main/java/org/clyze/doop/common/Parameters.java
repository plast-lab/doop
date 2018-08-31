package org.clyze.doop.common;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.clyze.doop.util.filter.ClassFilter;
import org.clyze.doop.util.filter.GlobClassFilter;

/**
 * This class contains common parameters for Doop Java front-ends.
 */
public abstract class Parameters {
    private List<String> _inputs = new ArrayList<>();
    private List<String> _dependencies = new ArrayList<>();
    private final List<String> _platformLibs = new ArrayList<>();
    private String _outputDir = null;
    private String _extraSensitiveControls = "";
    private ClassFilter applicationClassFilter;
    public boolean _android = false;
    public Integer _cores = null;
    public String _androidJars = null;
    public String _seed = null;
    public String _specialCSMethods = null;
    public String _rOutDir = null;
    public FactsSubSet _factsSubSet = null;
    private boolean _noFacts = false;

    public enum FactsSubSet { APP, APP_N_DEPS, PLATFORM }

    protected Parameters() {
        setAppRegex("**");
    }

    private void setAppRegex(String regex) {
        applicationClassFilter = new GlobClassFilter(regex);
    }

    public void setInputs(List<String> inputs) {
        _inputs = inputs;
    }

    public List<String> getInputs() {
        return _inputs;
    }

    public List<String> getPlatformLibs() {
        return _platformLibs;
    }

    public void setOutputDir(String outputDir) {
        _outputDir = outputDir;
    }

    public String getOutputDir() {
        return _outputDir;
    }

    public boolean isApplicationClass(String className) {
        return applicationClassFilter.matches(className);
    }

    public List<String> getInputsAndDependencies() {
        List<String> ret = new ArrayList<>(_inputs);
        ret.addAll(_dependencies);
        return ret;
    }

    public List<String> getDependenciesAndPlatformLibs() {
        List<String> ret = new ArrayList<>(_dependencies);
        ret.addAll(_platformLibs);
        return ret;
    }

    public List<String> getAllInputs() {
        List<String> ret = new ArrayList<>(_inputs);
        ret.addAll(_dependencies);
        ret.addAll(_platformLibs);
        return ret;
    }

    public boolean noFacts() {
        return _noFacts;
    }

    public String getExtraSensitiveControls() {
        return _extraSensitiveControls;
    }

    public List<String> getDependencies() {
        return _dependencies;
    }

    public void setDependencies(List<String> deps) {
        _dependencies = deps;
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
            _android = true;
            _androidJars = args[i];
            break;
        case "-i":
            i = shift(args, i);
            _inputs.add(args[i]);
            break;
        case "-l":
            i = shift(args, i);
            _platformLibs.add(args[i]);
            break;
        case "-lsystem":
            String javaHome = System.getProperty("java.home");
            _platformLibs.add(javaHome + File.separator + "lib" + File.separator + "rt.jar");
            _platformLibs.add(javaHome + File.separator + "lib" + File.separator + "jce.jar");
            _platformLibs.add(javaHome + File.separator + "lib" + File.separator + "jsse.jar");
            break;
        case "-ld":
            i = shift(args, i);
            _dependencies.add(args[i]);
            break;
        case "-d":
            i = shift(args, i);
            setOutputDir(args[i]);
            break;
        case "--application-regex":
            i = shift(args, i);
            setAppRegex(args[i]);
            break;
        case "--fact-gen-cores":
            i = shift(args, i);
            try {
                _cores = new Integer(args[i]);
            } catch (NumberFormatException nfe) {
                System.out.println("Invalid cores argument: " + args[i]);
            }
            break;
        case "--facts-subset":
            i = shift(args, i);
            _factsSubSet = Parameters.FactsSubSet.valueOf(args[i]);
            break;
        case "--R-out-dir":
            i = shift(args, i);
            _rOutDir = args[i];
            break;
        case "--extra-sensitive-controls":
            i = shift(args, i);
            _extraSensitiveControls = args[i];
            break;
        case "--seed":
            i = shift(args, i);
            _seed = args[i];
            break;
        case "--special-cs-methods":
            i = shift(args, i);
            _specialCSMethods = args[i];
            break;
        case "--noFacts":
            _noFacts = true;
            break;
        default:
            return -1;
        }
        return i;
    }

}
