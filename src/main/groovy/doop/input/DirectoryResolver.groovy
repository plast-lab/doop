package doop.input
import doop.core.Helper
/**
 * Resolves the input as a directory that contains jar files.
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 23/3/2015
 */
class DirectoryResolver implements InputResolver {

    @Override
    String name() {
        return "directory"
    }

    @Override
    void resolve(String input, DefaultInputResolutionContext ctx) {
        File f = Helper.checkDirectoryOrThrowException(input, "Not a directory input: $input")

        def filter = Helper.extensionFilter("jar")

        List<File> filesInDir = []
        f.listFiles(filter).each { File file ->
            filesInDir.add(file)
        }

        ctx.set(input, filesInDir.sort{ it.toString() });
    }
}
