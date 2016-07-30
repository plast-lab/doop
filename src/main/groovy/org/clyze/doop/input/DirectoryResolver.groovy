package org.clyze.doop.input

import org.clyze.doop.system.FileOps

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
        def dir = FileOps.findDirOrThrow(input, "Not a directory input: $input")
        def filter = FileOps.extensionFilter("jar")

        List<File> filesInDir = []
        dir.listFiles(filter).each { File file -> filesInDir.add(file) }

        ctx.set(input, filesInDir.sort{ it.toString() });
    }
}
