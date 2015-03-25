package doop.input

import doop.core.Helper

/**
 * Resolves the input as a local file.
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 23/3/2015
 */
class FileResolver implements InputResolver {

    @Override
    String name() {
        return "file"
    }

    @Override
    void resolve(String input, DefaultInputResolutionContext ctx) {
        File file = Helper.checkFileOrThrowException(input, "Not a valid file input: $input")
        ctx.set(input, file)
    }
}
