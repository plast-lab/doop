package doop.preprocess

import doop.Analysis
import doop.AnalysisOption
import doop.Helper
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * A native c preprocessor (invokes the cpp executable).
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 2/9/2014
 */
class CppPreprocessor implements Preprocessor {

    private Log logger = LogFactory.getLog(getClass())

    @Override
    void init() { }

    @Override
    void preprocess(Analysis analysis, String basePath, String input, String output, String... includes) {

        logger.debug("Preprocessing $input -> $output")

        //Add the appropriate analysis options to the preprocessor
        List<AnalysisOption> macros = analysis.options.values().findAll { AnalysisOption option ->
            //if the value of the option is true, we add it to as a macro
            option.forPreprocessor && option.value
        }

        String macroCli = macros.collect{AnalysisOption option -> 
            if (option.value instanceof Boolean)
                return "-D${option.id}" 
            else 
                return "-D${option.id}='\"${option.value}\"'"
        }.join(" ")

        if (includes) {
            String includeArgs = includes.collect{ "-include $it" }.join(" ")
            Helper.execCommand("cpp -CC -P $macroCli $basePath/$input $includeArgs $output", analysis.commandsEnvironment)
        }
        else {
            Helper.execCommand("cpp -CC -P $macroCli $basePath/$input $output", analysis.commandsEnvironment)
        }
    }
}
