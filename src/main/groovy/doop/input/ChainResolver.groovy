package doop.input

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 23/3/2015
 */
class ChainResolver implements InputResolver {
    private final List<InputResolver> resolvers
    protected Log logger = LogFactory.getLog(getClass())


    ChainResolver(InputResolver... resolvers) {
        this.resolvers = resolvers
    }

    @Override
    String name() {
        return "chain"
    }

    @Override
    void resolve(String input, InputResolutionContext ctx) {
        boolean resolved = false
        for(InputResolver resolver : resolvers) {
            try {
                resolver.resolve(input, ctx)
                resolved = true
            }
            catch(e) {
                logger.warn e.getMessage()
                //logger.warn Helper.stackTraceToString(e)
            }
            if (resolved) {
                logger.debug "Resolved input $input -> ${resolver.name()} - ${ctx.get(input)}"
                return
            }
        }

        throw new RuntimeException("Not a valid input: $input")
    }
}
