package org.clyze.doop.utils

import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.doop.core.Doop
import org.clyze.utils.Executor
import org.clyze.utils.JHelper

if (args.size() < 7) {
    println "Usage:"
    println "  ./gradlew souffleScript -Pargs='<scriptFilePath> <factsDirPath> <outDirPath> <cacheDirPath> <jobs> <profile> <debug> <provenance> <recompile> <via-ddlog> <interpret>'"
    println "Parameters:"
    println "  scriptFilePath   the Datalog file to evaluate"
    println "  factsDirPath     the directory containing the input facts"
    println "  outDirPath       the directory where analysis will write intermediate and final results"
    println "  cacheDirPath     the cache directory (e.g. \$DOOP_HOME/cache)"
    println "  jobs             the number of jobs to use when running (e.g., 4)"
    println "  profile          'true' or 'false'"
    println "  debug            'true' or 'false'"
    println "  provenance       'true' or 'false'"
    println "  recompile        'true' or 'false'"
    println "  via-ddlog        'true' or 'false'"
    println "  interpret        'true' or 'false'"
    return
}

def (String scriptFilePath, String factsDirPath, String outDirPath, String cacheDirPath, String jobs, String profile, String debug, String provenance, String recompile, String viaDDlog, String interpret) = args
def outDir = new File(outDirPath)
outDir.mkdirs()
def cacheDir = new File(cacheDirPath)
cacheDir.mkdirs()
def env = [:]
env.putAll(System.getenv())

try {
    JHelper.tryInitLogging("INFO", Doop.doopLog ?: "$outDir/logs", true, Doop.LOG_NAME)
} catch (IOException ex) {
    System.err.println("WARNING: Could not initialize logging")
    throw DoopErrorCodeException.error19()
}

def script = SouffleScript.newScript(new Executor(outDir, env), cacheDir, viaDDlog.toBoolean())
SouffleOptions souffleOpts = new SouffleOptions()
souffleOpts.profile = profile.toBoolean()
souffleOpts.provenance = provenance.toBoolean()
souffleOpts.debug = debug.toBoolean()
souffleOpts.forceRecompile = recompile.toBoolean()
souffleOpts.jobs = jobs.toInteger()
File scriptFile = new File(scriptFilePath)
File factsDir = new File(factsDirPath)

if (interpret && interpret.toBoolean()) {
    script.interpretScript(scriptFile, outDir, factsDir, jobs.toInteger(), souffleOpts)
    return
} else {
    def generatedFile = script.compile(scriptFile, outDir, souffleOpts)
    println "Compilation time (sec)\t${script.compilationTime}\n"
    script.run(generatedFile, factsDir, outDir, 5000, null, souffleOpts)
}
println "Execution time (sec)\t${script.executionTime}\n"
