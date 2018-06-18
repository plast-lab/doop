package org.clyze.doop.utils

import org.apache.commons.logging.LogFactory
import org.clyze.doop.core.Doop
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
def logger = LogFactory.getLog(getClass())

def script = new SouffleScript(scriptFile, factsDir, outDir, cacheDir, executor, logger)
script.run(jobs.toInteger(), profile.toBoolean(), debug.toBoolean())

logger.info "Compilation time (sec)\t${script.compilationTime}\n"
logger.info "Execution time (sec)\t${script.executionTime}\n"
