package org.clyze.doop.input

import org.clyze.analysis.InputType
import org.clyze.utils.FileOps

class PythonDirectoryResolver implements InputResolver {

    String name() { "python directory" }

    void resolve(String input, InputResolutionContext ctx, InputType inputType) {
        def dir = FileOps.findDirOrThrow(input, "Not a directory input: $input")
        //def filter = FileOps.extensionFilter("jar")
        def subDirs = new ArrayList<File>()
        dir.eachDir {subDir -> subDirs.add(subDir)}
        //def filesInDir = dir.listFiles(filter).toList()
        ctx.set(input, [dir] + subDirs.sort { it.toString() }, inputType)
    }
}
