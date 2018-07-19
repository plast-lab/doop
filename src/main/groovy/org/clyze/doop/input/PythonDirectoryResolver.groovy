package org.clyze.doop.input

import org.clyze.analysis.InputType
import org.clyze.utils.FileOps

class PythonDirectoryResolver implements InputResolver {

    String name() { "python directory" }

    void resolve(String input, InputResolutionContext ctx, InputType inputType) {
        def dir = FileOps.findDirOrThrow(input, "Not a directory input: $input")
        //def filter = FileOps.extensionFilter("jar")
        def allFiles = [] as List
        def filter = FileOps.extensionFilter("py")
        def subDirs = new ArrayList<File>()
        def q = [dir] as Queue
        subDirs.add(dir)
        while(!q.isEmpty()) {
            File ddir = q.remove();
            allFiles.addAll(ddir.listFiles(filter).toList())
            println"Trying for dir " + ddir
            ddir.eachDir { subDir -> subDirs.add(subDir); q.add(subDir) }
        }

        println "Dirs " + subDirs
        //def filesInDir = dir.listFiles(filter).toList()
        ctx.set(input, allFiles.sort { it.toString() }, inputType)
    }
}
