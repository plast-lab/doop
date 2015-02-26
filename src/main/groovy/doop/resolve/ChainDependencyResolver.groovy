package doop.resolve

import doop.core.Analysis
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * A resolver that implements a resolution chain.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 22/10/2014
 */
class ChainDependencyResolver implements DependencyResolver {

    private final List<DependencyResolver> resolvers
    protected Log logger = LogFactory.getLog(getClass())


    ChainDependencyResolver(DependencyResolver... resolvers) {
        this.resolvers = resolvers
    }

    @Override
    File resolve(String dependency, Analysis analysis) {
        File f
        for(DependencyResolver resolver : resolvers) {
            try {
                f = resolver.resolve(dependency, analysis)
            }
            catch(e) {
                logger.warn e.getMessage()
                //logger.warn Helper.stackTraceToString(e)
            }
            if (f) {
                logger.debug "Resolved dependency $dependency -> $f"
                return f
            }
        }

        throw new RuntimeException("Not a valid dependency: $dependency")
    }
}
