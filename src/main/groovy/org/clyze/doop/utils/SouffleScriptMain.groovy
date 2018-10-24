package org.clyze.doop.utils

import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.doop.core.Doop
import org.clyze.utils.Executor
import org.clyze.utils.Helper

if (args.size() < 7) {
    println "Usage:"
    println "  ./gradlew souffleScript -Pargs='<scriptFilePath> <factsDirPath> <outDirPath> <cacheDirPath> <jobs> <profile> <debug>'"
    println "Parameters:"
    println "  scriptFilePath   the Datalog file to evaluate"
    println "  factsDirPath     the directory containing the input facts"
    println "  outDirPath       the directory where facts will be written"
    println "  cacheDirPath     the cache directory (e.g. \$DOOP_HOME/cache)"
    println "  jobs             the number of jobs to use when running (e.g., 4)"
    println "  profile          'true' or 'false'"
    println "  debug            'true' or 'false'"
    return
}

def (String scriptFilePath, String factsDirPath, String outDirPath, String cacheDirPath, String jobs, String profile, String debug) = args
def outDir = new File(outDirPath)
outDir.mkdirs()
def cacheDir = new File(cacheDirPath)
cacheDir.mkdirs()
def env = [:]
env.putAll(System.getenv())

try {
    Helper.tryInitLogging("INFO", Doop.doopLog ?: "$outDir/logs", true)
} catch (IOException ex) {
    System.err.println("Warning: could not initialize logging")
    throw new DoopErrorCodeException(19)
}

def script = new SouffleScript(new Executor(outDir, env))
def generatedFile = script.compile(new File(scriptFilePath), outDir, cacheDir, profile.toBoolean(), debug.toBoolean())
script.run(generatedFile, new File(factsDirPath), outDir, jobs.toInteger(), 5000, null)

println "Compilation time (sec)\t${script.compilationTime}\n"
println "Execution time (sec)\t${script.executionTime}\n"
