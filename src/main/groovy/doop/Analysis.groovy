package doop
import doop.preprocess.JcppPreprocessor
import org.apache.commons.io.FilenameUtils

import java.lang.reflect.Method
/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 9/7/2014
 *
 * A DOOP analysis that holds all the relevant options (vars, paths, etc) and performs the required steps automatically.
 */
class Analysis {

    private final JcppPreprocessor jcpp = new JcppPreprocessor()

    /**
     * The unique identifier of the analysis (that determines the caching)
     */
    String id

    /**
     * The name of the analysis (that determines the logic)
     */
    String name

    /**
     * The output dir of the analysis results and intermediate files
     */
    String outDir

    /**
     * The analysis options
     */
    Map<String, AnalysisOption> options

    String inputFilesChecksum
    String logicFilesChecksum

    protected Analysis() {}

    /**
     * Precprocess the logic of the analysis
     */
    void preprocessLogic() {

        //TODO: Run them in parallel

        String basePath = "${Doop.LOGIC_PATH}/${name}"
        String baseLibPath = "${Doop.LOGIC_PATH}/library"
        String baseClientPath = "${Doop.LOGIC_PATH}/client"

        if (options.DACAPO.value || options.DACAPO_BACH.value) {
            //process the dacapo logic
        }
        else {
            jcpp.preprocess(this, basePath, "declarations.logic", "${outDir}/${name}-declarations.logic")
            jcpp.preprocess(this, basePath, "delta.logic",        "${outDir}/${name}-delta.logic")
            jcpp.preprocess(this, basePath, "analysis.logic",     "${outDir}/${name}.logic")
        }

        jcpp.preprocess(this, baseLibPath,    "reflection-delta.logic",     "${outDir}/reflection-delta.logic")
        jcpp.preprocess(this, baseClientPath, "exception-flow-delta.logic", "${outDir}/exception-flow-delta.logic")
        jcpp.preprocess(this, baseClientPath, "auxiliary-heap-allocations-delta.logic",
                        "${outDir}/auxiliary-heap-allocations-delta.logic"
        )
    }

    /**
     * Runs jphantom if phantom refs are not allowed
     */
    void dealWithPhantomRefs(){
        if (!options.ALLOW_PHANTOM.value) {

            String jar = options.JAR.value
            String jarName = FilenameUtils.getBaseName(jar)
            String jarExt = FilenameUtils.getExtension(jar)
            String newJar = "${jarName}-complemented.${jarExt}"
            String[] params = [jar, "-o", "${outDir}/$newJar", "-d", "${outDir}/phantoms", "-v", "0"]
            println "Running jphantom ${params.join(' ')}"

            ClassLoader loader = phantomClassLoader()
            runWithClassLoader (loader) {
                //we invoke the main method reflectively to avoid adding JPhantom as a compile-time dependency
                Class[] parameterTypes = [String[].class]
                Object[] args = [params]
                Method main = Class.forName("jphantom.Driver").getMethod("main", parameterTypes)
                main.invoke(null, args)
            }
        }
    }

    /**
     * @return A string representation of the analysis
     */
    String toString() {
        return [id:id, name:name, outDir:outDir].collect { Map.Entry entry -> "${entry.key}=${entry.value}" }.join("\n") +
               "\n" +
               options.values().collect { AnalysisOption option -> "$option" }.join("\n")
    }

    /**
     * Runs the closure in the current thread using the specified class loader
     * @param cl - the class loader to use
     * @param closure - the closure to run
     */
    private static void runWithClassLoader(ClassLoader cl, Closure closure) {
        Thread currentThread = Thread.currentThread()
        ClassLoader oldLoader = currentThread.getContextClassLoader()
        currentThread.setContextClassLoader(cl)
        try {
            closure.call()
        } catch (e) {
            throw new RuntimeException(e.getMessage(), e)
        }
        finally {
            currentThread.setContextClassLoader(oldLoader)
        }
    }

    private static ClassLoader phantomClassLoader() {
        //TODO: for now, we hard-code the jphantom jar
        String jphantom = "${Doop.DOOP_HOME}/lib/jphantom-1.0-jar-with-dependencies.jar"
        File f = new File(jphantom)
        if (f.exists() && f.isFile()) {
            URL[] classpath = [f.toURI().toURL()]
            return new URLClassLoader(classpath)
        }
        else {
            throw new RuntimeException("JPhantom jar missing: $jphantom")
        }
    }

}
