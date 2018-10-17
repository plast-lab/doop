package org.clyze.doop.soot;

import java.util.Collection;
import java.util.ArrayList;
import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.common.Parameters;

public class SootParameters extends Parameters {
    enum Mode { INPUTS, FULL }

    Mode _mode = null;
    String _main = null;
    boolean _ssa = false;
    boolean _allowPhantom = false;
    boolean _generateJimple = false;
    boolean _ignoreWrongStaticness = false;
    boolean _reportPhantoms = true;
    boolean _failOnMissingClasses = false;
    String _androidJars = null;
    private boolean _runFlowdroid = false;
    private boolean _toStdout = false;
    private Collection<String> extraClassesToResolve = new ArrayList<>();

    public boolean getRunFlowdroid() {
      return this._runFlowdroid;
    }

    public Collection<String> getExtraClassesToResolve() {
        return extraClassesToResolve;
    }

    @Override
    protected int processNextArg(String[] args, int i) throws DoopErrorCodeException {
        switch (args[i]) {
        case "--full":
            if (this._mode != null) {
                System.err.println("error: duplicate mode argument");
                throw new DoopErrorCodeException(1);
            }
            this._mode = SootParameters.Mode.FULL;
            break;
        case "--main":
            i = shift(args, i);
            this._main = args[i];
            break;
        case "--ssa":
            this._ssa = true;
            break;
        case "--allow-phantom":
            this._allowPhantom = true;
            break;
        case "--android-jars":
            i = shift(args, i);
            _android = true;
            _androidJars = args[i];
            break;
        case "--run-flowdroid":
            this._runFlowdroid = true;
            break;
        case "--generate-jimple":
            this._generateJimple = true;
            break;
        case "--stdout":
            this._toStdout = true;
            break;
        case "--ignoreWrongStaticness":
            this._ignoreWrongStaticness = true;
            break;
        case "--dont-report-phantoms":
            this._reportPhantoms = false;
            break;
        case "--also-resolve":
            i = shift(args, i);
            extraClassesToResolve.add(args[i]);
            break;
        case "--failOnMissingClasses":
            _failOnMissingClasses = true;
            break;
        case "-h":
        case "--help":
        case "-help":
            System.err.println("\nusage: [options] file");
            showHelp();
            throw new DoopErrorCodeException(0);
        default:
            return super.processNextArg(args, i);
        }
        return i;
    }

    static void showHelp() {
            System.err.println("options:");
            System.err.println("  --main <class>                        Specify the name of the main class");
            System.err.println("  --ssa                                 Generate SSA facts, enabling flow-sensitive analysis");
            System.err.println("  --full                                Generate facts by full transitive resolution");
            System.err.println("  -d <directory>                        Specify where to generate csv fact files");
            System.err.println("  -l <archive>                          Find (library) classes in jar/zip archive");
            System.err.println("  -ld <archive>                         Find (dependency) classes in jar/zip archive");
            System.err.println("  -lsystem                              Find classes in default system classes");
            System.err.println("  --facts-subset                        Produce facts only for a subset of the given classes");
            System.err.println("  --ignore-factgen-errors               Continue with the analysis even if fact generation fails");
            System.err.println("  --noFacts                             Don't generate facts (just empty files -- used for debugging)");
            System.err.println("  --ignoreWrongStaticness               Ignore 'wrong static-ness' errors in Soot");
            System.err.println("  --failOnMissingClasses                Terminate if classes are missing");
            System.err.println("  --also-resolve <class>                Force resolution of class that may not be found automatically.");
            System.err.println("Jimple/Shimple generation:");
            System.err.println("  --generate-jimple                     Generate Jimple/Shimple files instead of facts");
            System.err.println("  --stdout                              Write Jimple/Shimple to stdout");
            System.err.println("Android options:");
            System.err.println("  --android-jars <archive>              The main android library jar (for Android apk inputs). The same jar should be provided in the -l option");
            System.err.println("  --decode-apk                          Decompress apk input in facts directory.");
            System.err.println("  --extra-sensitive-controls <controls> A list of extra sensitive layout controls (format: \"id1,type1,parent_id1,id2,...\").");
            System.err.println("  --R-out-dir <directory>               Specify where to generate R code (when linking AAR inputs)");
    }

    /**
     * Finishes command-line argument processing (e.g., checks for incompatible
     * or missing options or sets defaults).
     * @throws DoopErrorCodeException    exception containing error code
     */
    @Override
    protected void finishArgProcessing() throws DoopErrorCodeException {
        super.finishArgProcessing();

        if (_mode == null)
            _mode = SootParameters.Mode.INPUTS;

        if (_toStdout && !_generateJimple) {
            System.err.println("error: --stdout must be used with --generate-jimple");
            throw new DoopErrorCodeException(7);
        } else if (_toStdout && getOutputDir() != null) {
            System.err.println("error: --stdout and -d options are not compatible");
            throw new DoopErrorCodeException(2);
        } else if ((getInputs().stream().anyMatch(s -> s.endsWith(".apk") || s.endsWith(".aar"))) &&
                (!_android)) {
            System.err.println("error: the --platform parameter is mandatory for .apk/.aar inputs, run './doop --help' to see the valid Android platform values");
            throw new DoopErrorCodeException(3);
        } else if (_android && _androidJars == null) {
            System.err.println("internal error: bad configuration for Android analysis mode, missing Android .jar");
            throw new DoopErrorCodeException(21);
        }

        if (!_toStdout && getOutputDir() == null)
            setOutputDir(System.getProperty("user.dir"));
    }
}
