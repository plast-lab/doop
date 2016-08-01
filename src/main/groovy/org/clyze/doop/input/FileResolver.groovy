package org.clyze.doop.input

import org.clyze.doop.core.Helper

/**
 * Resolves the input as a local file.
 */
class FileResolver implements InputResolver {

    @Override
    String name() {
        return "file"
    }

    @Override
    void resolve(String input, InputResolutionContext ctx) {
        File file = Helper.checkFileOrThrowException(input, "Not a file input: $input")
        ctx.set(input, file)
    }
}
