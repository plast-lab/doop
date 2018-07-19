package org.clyze.doop.utils

import org.clyze.utils.Executor
import org.clyze.utils.Helper

def (String scriptFilePath, String factsDirPath, String outDirPath, String cacheDirPath, String jobs, String profile, String debug) = args
def outDir = new File(outDirPath)
outDir.mkdirs()
def cacheDir = new File(cacheDirPath)
cacheDir.mkdirs()
def env = [:]
env.putAll(System.getenv())

Helper.initLogging("INFO", "$outDir/logs", true)

def script = new SouffleScript()
script.run(new File(scriptFilePath), new File(factsDirPath), outDir, cacheDir,
		new Executor(outDir, env), jobs.toInteger(), 5000, null,
		profile.toBoolean(), debug.toBoolean())

println "Compilation time (sec)\t${script.compilationTime}\n"
println "Execution time (sec)\t${script.executionTime}\n"
