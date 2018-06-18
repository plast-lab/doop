package org.clyze.doop.utils

import org.clyze.utils.Executor
import org.clyze.utils.Helper

def (String scriptFilePath, String factsDirPath, String outDirPath, String cacheDirPath, String jobs, String profile, String debug) = args
def scriptFile = new File(scriptFilePath)
def factsDir = new File(factsDirPath)
def outDir = new File(outDirPath)
outDir.mkdirs()
def cacheDir = new File(cacheDirPath)
cacheDir.mkdirs()

def env = [:]
env.putAll(System.getenv())
def executor = new Executor(outDir, env)

Helper.initLogging("INFO", "$outDir/logs", true)

def script = new SouffleScript(scriptFile, factsDir, outDir, cacheDir, executor)
script.run(jobs.toInteger(), 5000, profile.toBoolean(), debug.toBoolean())

println "Compilation time (sec)\t${script.compilationTime}\n"
println "Execution time (sec)\t${script.executionTime}\n"
