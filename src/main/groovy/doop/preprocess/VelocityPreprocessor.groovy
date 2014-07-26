package doop.preprocess

import doop.Analysis
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.app.event.EventCartridge
import org.apache.velocity.app.event.implement.IncludeRelativePath
import org.apache.velocity.runtime.RuntimeConstants

/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 9/7/2014
 *
 * The velocity preprocessor.
 * This is experimental and may be removed.
 */
@Singleton class VelocityPreprocessor implements Preprocessor {

    private static final String ENCODING = "UTF-8"

    Log logger = LogFactory.getLog(getClass())
    VelocityEngine engine = new VelocityEngine()

    void init()  {
        Properties props = new Properties()

        props.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, "org.apache.velocity.runtime.log.Log4JLogChute" )
        props.setProperty("runtime.log.logsystem.log4j.logger", logger.getClass().getName())
        props.setProperty("input.encoding", "UTF-8")
        props.setProperty("output.encoding", "UTF-8")
        props.setProperty("resource.loader", "classpath")
        props.setProperty("classpath.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader")

        logger.debug("Preprocessor (Velocity) properties: " + props);
        engine.init(props);
    }

    void preprocess(Analysis analysis, String basePath, String logic, String out) {
        VelocityContext ctx = new VelocityContext([
            analysis:analysis
        ])

        EventCartridge ec = new EventCartridge()
        ec.addIncludeEventHandler(new IncludeRelativePath())
        ec.attachToContext(ctx)

        StringWriter sw = new StringWriter(4096)
        engine.mergeTemplate(logic, ENCODING, ctx, sw)
        println sw.toString()
    }
}
