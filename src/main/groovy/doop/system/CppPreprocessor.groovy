package doop.system

import doop.core.Analysis
import doop.core.AnalysisOption
import doop.core.Helper
import groovy.transform.TypeChecked;
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * A native c preprocessor (invokes the cpp executable).
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 2/9/2014
 */
@TypeChecked
class CppPreprocessor {

    private Log logger = LogFactory.getLog(getClass())

    void preprocess(Analysis analysis, String basePath, String input, String output, String... includes) {

        logger.debug("Preprocessing $input -> $output")

        //Add the appropriate analysis options to the preprocessor
        Collection<AnalysisOption> macros = analysis.options.values().findAll { AnalysisOption option ->
            //if the value of the option is true, we add it to as a macro
            option.forPreprocessor && option.value
        }

        def macroCli = macros.collect{AnalysisOption option -> 
            if (option.value instanceof Boolean)
                return "-D${option.id}" 
            else 
                return "-D${option.id}='\"${option.value}\"'"
        }.join(" ")

        def includeArgs = includes.collect{ "-include $it" }.join(" ")
        new Executor(analysis.commandsEnvironment).execute("cpp -P $macroCli $basePath/$input $includeArgs $output")
    }
}
