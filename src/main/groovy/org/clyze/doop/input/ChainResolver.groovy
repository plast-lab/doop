package org.clyze.doop.input

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.analysis.InputType

class ChainResolver implements InputResolver {

    private final List<InputResolver> resolvers
    protected Log logger = LogFactory.getLog(getClass())

    ChainResolver(InputResolver... resolvers) {
        this.resolvers = resolvers
    }

    String name() { "chain" }

    void resolve(String input, InputResolutionContext ctx, InputType inputType) {
        for(InputResolver resolver : resolvers) {
            try {
                logger.debug "Resolving input: $input via ${resolver.name()}"
                resolver.resolve(input, ctx, inputType)
                logger.debug "Resolved input $input -> ${ctx.get(input, inputType)}"
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
