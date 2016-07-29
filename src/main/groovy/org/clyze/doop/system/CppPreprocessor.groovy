package org.clyze.doop.system

import groovy.transform.TypeChecked;
import org.apache.commons.io.FileUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.doop.core.Analysis
import org.clyze.doop.core.AnalysisOption

/**
 * A native c preprocessor (invokes the cpp executable).
 */
@TypeChecked
class CppPreprocessor {

    private static Log logger = LogFactory.getLog(CppPreprocessor.class)

    static void preprocess(Analysis analysis, String input, String output, String... includes) {
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
    static void preprocessAtStart(Analysis analysis, String input, String output, String... includes) {
        def tmpFile = new File(FileUtils.getTempDirectory(), "tmpFile")
        preprocess(analysis, output, tmpFile.getCanonicalPath(), (includes + [input]) as String[])
        FileUtils.copyFile(tmpFile, new File(output))
        FileUtils.deleteQuietly(tmpFile)
    }
}
