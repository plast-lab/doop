package org.clyze.doop.common;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.clyze.doop.util.filter.ClassFilter;
import org.clyze.doop.util.filter.GlobClassFilter;

/**
 * This class handles common parameters for Doop Java front-ends.
 */
public class Parameters {
    private List<String> _inputs = new ArrayList<>();
    private List<String> _dependencies = new ArrayList<>();
    private final List<String> _platformLibs = new ArrayList<>();
    private String _outputDir = null;
    private String _logDir = null;
    private String _extraSensitiveControls = "";
    private ClassFilter applicationClassFilter;
    public boolean _android = false;
    public Integer _cores = null;
    public String _seed = null;
    public String _rOutDir = null;
    public FactsSubSet _factsSubSet = null;
    private boolean _noFacts = false;
    public boolean _ignoreFactGenErrors = false;
    private boolean _decodeApk = false;

    public enum FactsSubSet { APP, APP_N_DEPS, PLATFORM }

    public Parameters() {
        setAppRegex("**");
    }

    public void initFromArgs(String[] args) throws DoopErrorCodeException {
        int i = 0, last_i;
        while (i < args.length) {
            last_i = processNextArg(args, i);
            if (last_i == -1)
                throw new RuntimeException("Bad argument: " + args[i]);
            i = last_i + 1;
        }

        finishArgProcessing();
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

    protected void setOutputDir(String outputDir) {
        _outputDir = outputDir;
    }

    public String getOutputDir() {
        return _outputDir;
    }

    public String getLogDir() {
        return _logDir;
    }

    public boolean isApplicationClass(String className) {
        return applicationClassFilter.matches(className);
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

    public boolean getDecodeApk() {
        return _decodeApk;
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
    protected int processNextArg(String[] args, int i) throws DoopErrorCodeException {
        switch (args[i]) {
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
        case "--log-dir":
            i = shift(args, i);
            _logDir = args[i];
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
        case "--no-facts":
            _noFacts = true;
            break;
        case "--ignore-factgen-errors":
            _ignoreFactGenErrors = true;
            break;
        case "--android":
            _android = true;
            break;
        case "--decode-apk":
            _decodeApk = true;
            break;
        default:
            return -1;
        }
        return i;
    }

    protected void finishArgProcessing() throws DoopErrorCodeException {
        // For some facts-subset values, some options will be ignored (cleared).
        if (_factsSubSet == FactsSubSet.APP) {
            _dependencies.clear();
            _platformLibs.clear();
        } else if (_factsSubSet == FactsSubSet.APP_N_DEPS)
            _platformLibs.clear();
        else if (_factsSubSet == FactsSubSet.PLATFORM) {
            _inputs.clear();
            _dependencies.clear();
        } else if (_factsSubSet != null) {
            System.err.println("Illegal facts subset option: " + _factsSubSet);
            throw new DoopErrorCodeException(4);
        } else if (getOutputDir() == null) {
            System.err.println("Error: no output facts directory.");
            throw new DoopErrorCodeException(16);
        }

        if (_logDir == null) {
            _logDir = getOutputDir() + File.separator + "logs";
            System.err.println("No logs directory set, using: " + _logDir);
        }
    }
}
