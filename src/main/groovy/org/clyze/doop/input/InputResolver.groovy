package org.clyze.doop.input

import org.clyze.analysis.InputType

/**
 * A resolver for inputs.
 */
interface InputResolver {
    void resolve(String input, InputResolutionContext ctx, InputType inputType)
    String name()
}
