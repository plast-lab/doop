package org.clyze.doop.input

/**
 * A resolver for inputs.
 */
interface InputResolver {
    void resolve(String input, InputResolutionContext ctx, InputType inputType)
    String name()
}
