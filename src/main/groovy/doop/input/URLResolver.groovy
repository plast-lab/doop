package doop.input

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils

/**
 * Resolves the input as a URL.
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 23/3/2015
 */
class URLResolver implements InputResolver{

    @Override
    String name() {
        return "url"
    }

    @Override
    void resolve(String input, InputResolutionContext ctx) {
        URL url
        try {
            url = new URL(input)
        }
        catch(e) {
            throw new RuntimeException("Not a valid URL input: $input", e)
        }


        try {
            File tmpFile = File.createTempFile(FilenameUtils.getName(input), FilenameUtils.getExtension(input))
            FileUtils.copyURLToFile(url, tmpFile)
            tmpFile.deleteOnExit()
            ctx.set(input, tmpFile)
        }
        catch(e) {
            throw new RuntimeException("Not a valid URL input: $input", e)
        }
    }
}
