package org.clyze.doop.input

import org.clyze.analysis.InputType

/**
 * A resolver for inputs.
 */
interface InputResolver {

	String name()

	void resolve(String input, InputResolutionContext ctx, InputType inputType)
}
