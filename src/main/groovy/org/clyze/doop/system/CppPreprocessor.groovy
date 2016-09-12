package org.clyze.doop.system

import groovy.transform.TypeChecked
import org.apache.commons.io.FileUtils
import org.clyze.doop.core.Analysis
import org.clyze.doop.core.AnalysisOption

@TypeChecked
class CppPreprocessor {

    static void preprocessIfExists(Analysis analysis, String output, String input, String... includes) {
        if (new File(input).isFile())
            preprocess(analysis, output, input, includes)
        else {
            def tmpFile = new File(FileUtils.getTempDirectory(), "tmpFile")
            tmpFile.createNewFile()
            preprocess(analysis, output, tmpFile.getCanonicalPath(), includes)
            FileUtils.deleteQuietly(tmpFile)
        }
    }

    static void preprocess(Analysis analysis, String output, String input, String... includes) {
        def macroCli = analysis.options.values()
        .findAll { AnalysisOption option ->
            option.forPreprocessor && option.value
        }
        .collect{ AnalysisOption option ->
            if (option.value instanceof Boolean)
                return "-D${option.id}"
            else
                return "-D${option.id}='\"${option.value}\"'"
        }
        .join(" ")

        def includeArgs = includes.collect{ "-include $it" }.join(" ")
        new Executor(analysis.commandsEnvironment).execute("cpp -P $macroCli $input $includeArgs $output")
    }

    /**
     * Preprocess input file and put contents *in the beginning* of the output file.
     */
    static void includeAtStart(Analysis analysis, String output, String input, String... includes) {
        def tmpFile = new File(FileUtils.getTempDirectory(), "tmpFile")
        preprocess(analysis, tmpFile.getCanonicalPath(), output, (includes + [input]) as String[])
        FileUtils.copyFile(tmpFile, new File(output))
        FileUtils.deleteQuietly(tmpFile)
    }
}
