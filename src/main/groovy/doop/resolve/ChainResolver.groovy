package doop.resolve

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * A resolver that implements a resolution chain.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 22/10/2014
 */
class ChainResolver implements Resolver {

    private final List<Resolver> resolvers
    protected Log logger = LogFactory.getLog(getClass())


    ChainResolver(Resolver... resolvers) {
        this.resolvers = resolvers
    }

    @Override
    File resolve(String dependency, ResolutionContext ctx) {
        File f
        for(Resolver resolver : resolvers) {
            try {
                f = resolver.resolve(dependency, ctx)
            }
            catch(e) {
                logger.warn e.getMessage()
            }
            if (f) return f
        }

        throw new RuntimeException("Not a valid dependency: $dependency")
    }
}
