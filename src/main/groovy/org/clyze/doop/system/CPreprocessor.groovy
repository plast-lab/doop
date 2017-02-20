package org.clyze.doop.system

import groovy.transform.TypeChecked
import org.apache.commons.io.FileUtils
import org.clyze.analysis.*

@TypeChecked
class CPreprocessor {

    String   _macroCli
    Executor _executor

    CPreprocessor(Analysis analysis, Executor executor) {
        _macroCli = analysis.options.values()
        .findAll { AnalysisOption option ->
            option.forPreprocessor && option.value
        }
        .collect{ AnalysisOption option ->
            if (option.value instanceof Boolean) {
                System.out.println("-D${option.id}")
                return "-D${option.id}"
            }
            else {
                System.out.println("-D${option.id}='\"${option.value}\"'")
                return "-D${option.id}='\"${option.value}\"'"
            }
        }
        .join(" ")
        _executor = executor
    }

    void preprocessIfExists(String output, String input, String... includes) {
        if (new File(input).isFile())
            preprocess(output, input, includes)
        else {
            def tmpFile = new File(FileUtils.getTempDirectory(), "tmpFile")
            tmpFile.createNewFile()
            preprocess(output, tmpFile.getCanonicalPath(), includes)
            FileUtils.deleteQuietly(tmpFile)
        }
    }

    void preprocess(String output, String input, String... includes) {
        def includeArgs = includes.collect{ "-include $it" }.join(" ")
        _executor.execute("cpp -P $_macroCli $input $includeArgs $output")
    }

    // Preprocess input file and put contents *in the beginning* of the output file.
    void includeAtStart(String output, String input, String... includes) {
        def tmpFile = new File(FileUtils.getTempDirectory(), "tmpFile")
        preprocess(tmpFile.getCanonicalPath(), output, (includes + [input]) as String[])
        FileUtils.copyFile(tmpFile, new File(output))
        FileUtils.deleteQuietly(tmpFile)
    }
}
