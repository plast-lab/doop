package org.clyze.doop.common;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.log4j.Logger;
import org.clyze.doop.util.filter.ClassFilter;
import org.clyze.doop.util.filter.GlobClassFilter;
import org.clyze.utils.ContainerUtils;
import org.clyze.utils.JHelper;

/**
 * This class handles common parameters for Doop Java front-ends.
 */
public class Parameters {
    public static final String OPT_REPORT_PHANTOMS = "--report-phantoms";

    private List<String> _inputs = new ArrayList<>();
    private List<String> _dependencies = new ArrayList<>();
    private final List<String> _platformLibs = new ArrayList<>();
    private String _outputDir = null;
    public boolean _debug = false;
    private String _logDir = null;
    private ClassFilter applicationClassFilter;
    public boolean _scanNativeCode = false;
    public boolean _nativeRadare = false;
    public boolean _nativeBuiltin = false;
    public boolean _nativeBinutils = false;
    public boolean _preciseNativeStrings = false;
    public boolean _android = false;
    public Integer _cores = null;
    public String _rOutDir = null;
    public FactsSubSet _factsSubSet = null;
    private boolean _noFacts = false;
    public boolean _ignoreFactGenErrors = false;
    private boolean _decodeApk = false;
    public boolean _extractMoreStrings = false;
    public boolean _writeArtifactsMap = false;
    public boolean _reportPhantoms = false;
    public boolean _dex = false;
    public boolean _legacyAndroidProcessing = false;
    public String _main = null;

    public enum FactsSubSet {
        APP, APP_N_DEPS, PLATFORM;
        public static Set<String> valueSet() {
            return Arrays.stream(values()).map(Enum::name).collect(Collectors.toSet());
        }
    }

    public Parameters() {
        setAppRegex("**");
    }

    private void processArgs(String[] args) throws DoopErrorCodeException {
        int i = 0, last_i;
        while (i < args.length) {
            last_i = processNextArg(args, i);
            if (last_i == -1)
                throw DoopErrorCodeException.error32("Bad argument: " + args[i]);
            i = last_i + 1;
        }
    }

    public void initFromArgs(String[] args) throws DoopErrorCodeException {
        processArgs(args);
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
            throw DoopErrorCodeException.error9();
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
        case "--debug":
            _debug = true;
            break;
        case "--log-dir":
            i = shift(args, i);
            _logDir = args[i];
            break;
        case "--application-regex":
            i = shift(args, i);
            setAppRegex(args[i]);
            break;
        case "--args-file":
            i = shift(args, i);
            processArgsFile(args[i]);
            break;
        case "--fact-gen-cores":
            i = shift(args, i);
            try {
                _cores = Integer.valueOf(args[i]);
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
        case "--no-facts":
            _noFacts = true;
            break;
        case "--ignore-factgen-errors":
            _ignoreFactGenErrors = true;
            break;
        case "--main":
            i = shift(args, i);
            if (this._main != null)
                System.err.println("WARNING: main class already set to " + this._main + ", ignoring value: " + args[i]);
            else
                this._main = args[i];
            break;
        case "--android":
            _android = true;
            break;
        case "--decode-apk":
            _decodeApk = true;
            break;
        case "--extract-more-strings":
            _extractMoreStrings = true;
            break;
        case "--write-artifacts-map":
            _writeArtifactsMap = true;
            break;
        case OPT_REPORT_PHANTOMS:
            this._reportPhantoms = true;
            break;
        case "--dex":
            this._dex = true;
            break;
        case "--scan-native-code":
            this._scanNativeCode = true;
            break;
        case "--native-backend-radare":
            this._nativeRadare = true;
            break;
        case "--native-backend-builtin":
            this._nativeBuiltin = true;
            break;
        case "--native-backend-binutils":
            this._nativeBinutils = true;
            break;
        case "--only-precise-native-strings":
            this._preciseNativeStrings = true;
            break;
        case "--legacy-android-processing":
            _legacyAndroidProcessing = true;
            break;
        default:
            return -1;
        }
        return i;
    }

    protected void finishArgProcessing() throws DoopErrorCodeException {
        if (getOutputDir() == null) {
            System.err.println("Error: no output facts directory.");
            throw DoopErrorCodeException.error16();
        }

        if (_logDir == null) {
            _logDir = getOutputDir() + File.separator + "logs";
            System.err.println("No logs directory set, using: " + _logDir);
        }
    }

    /**
     * Read command-line arguments from file, one per line.
     *
     * @param path   the file to use
     */
    private void processArgsFile(String path) throws DoopErrorCodeException {
        if (!(new File(path)).exists())
            throw new RuntimeException("Arguments file does not exist: " + path);

        List<String> lines = new LinkedList<>();
        try (Stream<String> stream = Files.lines(Paths.get(path))) {
            stream.forEach(lines::add);
        } catch (IOException ex) {
            throw new RuntimeException("Malformed arguments file: " + path);
        }

        String[] params = lines.toArray(new String[0]);
        processArgs(params);
    }

    public Logger initLogging(Class<?> c) throws IOException {
        String logDir = getLogDir();
        String logLevel = this._debug ? "DEBUG" : "INFO";
        JHelper.tryInitLogging(logLevel, logDir, true, "soot-fact-generator");
        Logger logger = Logger.getLogger(c);
        logger.info("Logging initialized, using directory: " + logDir);
        return logger;
    }

    /**
     * If inputs contain JARs (such as AAR/WAR inputs), extract and use
     * their JAR entries.
     *
     * @param tmpDirs      the set of temporary directories (for clean-up actions)
     */
    public void processFatArchives(Set<String> tmpDirs) {
        Set<String> jarLibs = ConcurrentHashMap.newKeySet();
        setInputs(ContainerUtils.toJars(getInputs(), false, jarLibs, tmpDirs));
        setDependencies(ContainerUtils.toJars(getDependencies(), false, jarLibs, tmpDirs));
        getDependencies().addAll(jarLibs);
        System.out.println("inputs = " + getInputs());
        System.out.println("libraries = " + getDependencies());
    }
}
