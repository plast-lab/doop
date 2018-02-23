package org.clyze.doop.input

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

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
    void resolve(String input, InputResolutionContext ctx, boolean isLib) {
        for(InputResolver resolver : resolvers) {
            try {
                logger.debug "Resolving input: $input via ${resolver.name()}"
                resolver.resolve(input, ctx, isLib)
                logger.debug "Resolved input $input -> ${ctx.get(input, isLib)}"
                return
            }
            catch(e) {
                logger.debug e.getMessage()
                //logger.warn Helper.stackTraceToString(e)
            }
        }

        throw new RuntimeException("Not a valid input: $input")
    }
}
