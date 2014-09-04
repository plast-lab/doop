package doop.preprocess
import doop.Analysis
import doop.AnalysisOption
import org.anarres.cpp.*
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 18/7/2014
 *
 * The C preprocessor for logic files.
 * This is experimental and may be removed.
 */
class JcppPreprocessor implements Preprocessor {

    private Log logger = LogFactory.getLog(getClass())
    private final Listener listener = new Listener()
    private final FileSystem fileSystem = new FileSystem() //used only for debugging

    @Override
    void init() {
        //do nothing
    }

    @Override
    void preprocess(Analysis analysis, String basePath, String input, String output) {

        logger.debug("Preprocessing $input -> $output")

        org.anarres.cpp.Preprocessor preprocessor = new org.anarres.cpp.Preprocessor()

        //preprocessor.setFileSystem(fileSystem)
        preprocessor.setListener(listener)
        preprocessor.setQuoteIncludePath([basePath])

        //Add the appropriate analysis options to the preprocessor
        analysis.options.values().findAll{ AnalysisOption option ->
            option.forPreprocessor
        }.each { AnalysisOption option ->
            //if the value of the option is true, we add its name as a macro
            if (option.value) {
                logger.debug("Adding macro ${option.id}")
                preprocessor.addMacro(option.id)
            }
        }

        preprocessor.addInput(new StringLexerSource("#include \"$input\" \n", true))

        BufferedWriter writer = new BufferedWriter(new FileWriter(output))
        writer.withWriter { Writer w ->
            Token token
            while((token = preprocessor.token()).getType() != Token.EOF) {
                w.append token.getText()
            }
        }

    }

    private class Listener implements PreprocessorListener {
        @Override
        void handleWarning(Source source, int line, int column, String msg) throws LexerException {
            logger.warn("warning: $source ($line,$column) - $msg")
        }

        @Override
        void handleError(Source source, int line, int column, String msg) throws LexerException {
            String message = "${source.getPath()}: $line,$column - $msg"
            logger.error("error: $message")
            throw new LexerException(message)
        }

        @Override
        void handleSourceChange(Source source, String event) {
            //logger.debug("sourceChange: $source - $event")
        }
    }
}
