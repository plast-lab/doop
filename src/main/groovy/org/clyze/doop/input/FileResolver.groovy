package org.clyze.doop.input

import org.clyze.utils.FileOps

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
        def file = FileOps.findFileOrThrow(input, "Not a file input: $input")
        ctx.set(input, file)
    }
}
