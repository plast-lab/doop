package org.clyze.doop.utils

import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.utils.Executor
import org.clyze.utils.Helper

def (String scriptFilePath, String factsDirPath, String outDirPath, String cacheDirPath, String jobs, String profile, String debug) = args
def outDir = new File(outDirPath)
outDir.mkdirs()
def cacheDir = new File(cacheDirPath)
cacheDir.mkdirs()
def env = [:]
env.putAll(System.getenv())

try {
    Helper.tryInitLogging("INFO", "$outDir/logs", true)
} catch (IOException ex) {
    System.err.println("Warning: could not initialize logging")
    throw new DoopErrorCodeException(15);
}

def script = new SouffleScript(new Executor(outDir, env))
def generatedFile = script.compile(new File(scriptFilePath), outDir, cacheDir, profile.toBoolean(), debug.toBoolean())
script.run(generatedFile, new File(factsDirPath), outDir, jobs.toInteger(), 5000, null)

println "Compilation time (sec)\t${script.compilationTime}\n"
println "Execution time (sec)\t${script.executionTime}\n"
