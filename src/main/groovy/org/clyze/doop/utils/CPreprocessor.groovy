package org.clyze.doop.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
import org.apache.commons.io.FileUtils
import org.clyze.analysis.Analysis
import org.clyze.analysis.AnalysisOption
import org.clyze.utils.Executor
import org.clyze.utils.OS

import java.nio.file.Files

@CompileStatic
@Log4j
class CPreprocessor {
    List<String> macroCli
    Executor executor
    boolean emitLineMarkers
    boolean logOutput

    CPreprocessor(Analysis analysis, Executor executor) {
        this(executor)
        macroCli = analysis.options.values()
                .findAll { AnalysisOption<?> option ->
            option.forPreprocessor && option.value
        }
        .collect { AnalysisOption<?> option ->
            if (option.id == 'DEFINE_CPP_MACRO')
                option.value.collect { "-D${it}" as String }
            else if (option.value instanceof Integer || option.value instanceof String)
                ["-D${option.id}=${option.value}" as String]
            else
                ["-D${option.id}" as String]
        }.flatten() as List<String>
        log.debug "Preprocessor: " + macroCli
    }

    CPreprocessor(Executor executor) {
        this.logOutput = false
        this.executor = executor
        this.emitLineMarkers = false
        macroCli = [] as List<String>
    }

    CPreprocessor enableLineMarkers() {
        emitLineMarkers = true
        return this
    }

    CPreprocessor disableLineMarkers() {
        emitLineMarkers = false
        return this
    }

    CPreprocessor enableLogOutput() {
        logOutput = true
        return this
    }

    CPreprocessor preprocessIfExists(String output, String input, String... includes) {
        if (new File(input).isFile())
            preprocess(output, input, includes)
        else {
            def tmpFile = createUniqueTmpFile()
            tmpFile.createNewFile()
            preprocess(output, tmpFile.getCanonicalPath(), includes)
            FileUtils.deleteQuietly(tmpFile)
        }
        return this
    }

    CPreprocessor preprocess(String output, String input, String... includes) {
        def cmd = [ getCPP() ]
        if (!emitLineMarkers) cmd << '-P'
        cmd += macroCli
        if (OS.macOS) {
            cmd += [ '-Isouffle-logic/main' ]
        }
        includes.each {
            if (OS.macOS) {
                def lastSlash = it.lastIndexOf(File.separator)
                if (lastSlash != -1) {
                    cmd += [ "-I${it.substring(0, lastSlash)}" as String ]
                }
            }
            cmd += ['-include', it as String]
        }
        cmd << input
        cmd << output
        log.debug "cpp command: ${cmd.join(' ')}"
        executor.execute(cmd) { if (logOutput) { log.info it } }
        return this
    }

    static String getCPP() {
        return System.getenv("DOOP_CPP") ?: 'cpp'
    }

    // Preprocess input file and put contents *in the beginning* of the output file.
    void includeAtStart(String output, String input, String... includes) {
        def tmpFile = createUniqueTmpFile()
        preprocess(tmpFile.getCanonicalPath(), output, (includes + [input]) as String[])
        FileUtils.copyFile(tmpFile, new File(output))
        FileUtils.deleteQuietly(tmpFile)
    }

    void includeAtEnd(String output, String input, String... includes) {
        includeAtEnd0(output, input, includes, this.&preprocess)
    }

    void includeAtEndIfExists(String output, String input, String... includes) {
        includeAtEnd0(output, input, includes, this.&preprocessIfExists)
    }

    // Implementation method called by *includeAtEnd* and *includeAtEndIfExists* with the
    // appropriate preprocess method (*preprocess* and *preprocessIfExists* respectively) as parameter.
    private static void includeAtEnd0(String output, String input, String[] includes, Closure closure) {
        def tmpFile = createUniqueTmpFile()
        closure(tmpFile.getCanonicalPath(), input, includes)
        tmpFile.withInputStream { stream ->
            new File(output) << stream
        }
        FileUtils.deleteQuietly(tmpFile)
    }

    private static File createUniqueTmpFile() {
        File f = Files.createTempFile(FileUtils.getTempDirectory().toPath(), "tmp", "pre").toFile()
        f.deleteOnExit()
        return f
    }
}
