package name.bravenboer.martin.soot;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
//import java.util.HashSet;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Set;

import soot.Body;
import soot.ClassProvider;
import soot.PhaseOptions;
import soot.Printer;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.shimple.Shimple;


public class BytecodeToJimple {

    public static enum Mode {
        FULL,
        /*CALLGRAPH,*/
        INPUTS
    }

    private static boolean _ssa = false;
    private static Mode _mode = null;
    private static boolean _toStdout = false;
    private static String _outputDir = null;
    private static List<String> _inputs = new ArrayList<>();
    private static List<String> _libraries = new ArrayList<>();
    private static String _suffix = ".jimple";

    private static int shift(String[] args, int index) {
        if (args.length == index + 1) {
            System.err.println("error: option " + args[index] + " requires an argument");
            System.exit(1);
        }
        return index + 1;
    }

    private static void usage(boolean failOnExit) {
        System.err.println("usage: bytecode2jimple [options] file...");
        System.err.println("options:");
        System.err.println("  --ssa           Generate Shimple instead of Jimple");
        System.err.println("  --full          Generate Jimple/Shimple files using full transitive resolution");
        System.err.println("  --stdout        Write Jimple/Shimple in stdout");
        System.err.println("  -d <directory>  Specify directory for generated Jimple/Shimple files (defaults to current dir)");
        System.err.println("  -l <archive>    Find classes in jar/zip archive");
        System.err.println("  -lsystem        Find classes in default system classes");
        System.err.println("  -h --help       Print this help message");
        System.exit(failOnExit ? 1 : 0);
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                usage(false);
            }

            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--ssa")) {
                    _ssa = true;
                    _suffix = ".shimple";
                }
                else if (args[i].equals("--full")) {
                    if (_mode != null) {
                        System.err.println("error: duplicate mode argument");
                        System.exit(1);
                    }
                    _mode = Mode.FULL;
                }
                /*
                else if (args[i].equals("--cg")) {
                    if (_mode != null) {
                        System.err.println("error: duplicate mode argument");
                        System.exit(1);
                    }
                    _mode = Mode.CALLGRAPH;
                }
                */
                else if (args[i].equals("--stdout")) {
                    _toStdout = true;
                }
                else if (args[i].equals("-d")) {
                    i = shift(args, i);
                    _outputDir = args[i];
                }
                else if (args[i].equals("-l")) {
                    i = shift(args, i);
                    _libraries.add(args[i]);
                }
                else if (args[i].equals("-lsystem")) {
                    String javaHome = System.getProperty("java.home");
                    _libraries.add(javaHome + File.separator + "lib" + File.separator + "rt.jar");
                    _libraries.add(javaHome + File.separator + "lib" + File.separator + "jce.jar");
                    _libraries.add(javaHome + File.separator + "lib" + File.separator + "jsse.jar");
                }
                else if (args[i].equals("-h") || args[i].equals("--help")) {
                    usage(false);
                }
                else {
                    if (args[i].charAt(0) == '-') {
                        System.err.println("error: unrecognized option: " + args[i]);
                        usage(true);
                    }
                    else {
                        _inputs.add(args[i]);
                    }
                }
            }

            if (_libraries.isEmpty()) {
                System.err.println("warning: no extra libraries specified (you may need -lsystem)");
            }

            if (_mode == null) {
                _mode = Mode.INPUTS;
            }

            if (_toStdout && _outputDir != null) {
                System.err.println("error: --stdout and -d options are not compatible");
                System.exit(1);
            }
            else if (!_toStdout && _outputDir == null) {
                _outputDir = System.getProperty("user.dir");
            }

            run();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void run() throws Exception {
        NoSearchingClassProvider provider = new NoSearchingClassProvider();

        for (String arg : _inputs) {
            if (arg.endsWith(".jar") || arg.endsWith(".zip")) {
                provider.addArchive(new File(arg));
            }
            else {
                provider.addClass(new File(arg));
            }
        }

        for (String lib: _libraries) {
            provider.addArchiveForResolving(new File(lib));
        }

        soot.SourceLocator.v().setClassProviders(Collections.singletonList((ClassProvider) provider));

        if (_mode == Mode.FULL /*|| _mode == Mode.CALLGRAPH*/) {
            soot.options.Options.v().set_full_resolver(true);
        }
        soot.options.Options.v().setPhaseOption("jb", "use-original-names:true");

        Scene scene = Scene.v();
        Collection<SootClass> classes = new ArrayList<>();
        for (String className : provider.getClassNames()) {
            scene.loadClass(className, SootClass.SIGNATURES);
            SootClass c = scene.loadClass(className, SootClass.BODIES);
            c.setApplicationClass();
            classes.add(c);
        }

        scene.loadNecessaryClasses();
        if (_mode == Mode.FULL) {
            classes = scene.getClasses();
        }
        /*
        else if (_mode == Mode.CALLGRAPH) {
            Map<String, String> opt = new HashMap<>();
            opt.put("verbose","true");
            opt.put("propagator","worklist");
            opt.put("simple-edges-bidirectional","false");
            opt.put("rta","enabled");
            opt.put("set-impl","double");
            opt.put("double-set-old","hybrid");
            opt.put("double-set-new","hybrid");

            soot.jimple.spark.SparkTransformer.v().transform("", opt);
            soot.jimple.toolkits.callgraph.CallGraphBuilder builder =
                new soot.jimple.toolkits.callgraph.CallGraphBuilder(scene.getPointsToAnalysis());
            builder.build();
            scene.getReachableMethods().update();

            Set<SootClass> set = new HashSet<>();
            Iterator<? extends MethodOrMethodContext> iterator = scene.getReachableMethods().listener();
            while (iterator.hasNext()) {
                SootMethod method = (SootMethod) iterator.next();
                System.err.println("class: " + method.getDeclaringClass());
                set.add(method.getDeclaringClass());
            }
            classes = set;
        }
        */

        write(classes);
    }

    private static void write(Collection<SootClass> classes) throws Exception {
        PrintWriter writer = null;
        if ( _toStdout ) {
            writer = new PrintWriter(System.out);
        }
        else {
            new File(_outputDir).mkdirs();
        }

        for (SootClass c : classes) {
            for (SootMethod m : c.getMethods()) {
                if ( m.isConcrete() ) {
                    m.retrieveActiveBody();

                    if ( _ssa ) {
                        Body b = m.getActiveBody();
                        b = Shimple.v().newBody(b);
                        m.setActiveBody(b);
                    }

                }
            }

            if ( _toStdout ) {
                Printer.v().printTo(c, writer);
                writer.flush();
            }
            else {
                writer = new PrintWriter(new File(_outputDir, c.getName() + _suffix));
                Printer.v().printTo(c, writer);
                writer.close();
            }

            if (writer.checkError()) {
                throw new RuntimeException("error: unknown error during writing to PrintWriter");
            }
        }
    }
}
