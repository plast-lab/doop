package org.clyze.doop.input
import org.clyze.doop.core.Helper
/**
 * Resolves the input as a directory that contains jar files.
 */
class DirectoryResolver implements InputResolver {

    @Override
    String name() {
        return "directory"
    }

    @Override
    void resolve(String input, InputResolutionContext ctx) {
        File f = Helper.checkDirectoryOrThrowException(input, "Not a directory input: $input")

        def filter = Helper.extensionFilter("jar")

        List<File> filesInDir = []
        f.listFiles(filter).each { File file ->
            filesInDir.add(file)
        }

        ctx.set(input, filesInDir.sort{ it.toString() });
    }
}
