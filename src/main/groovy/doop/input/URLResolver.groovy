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


        String name = FilenameUtils.getName(url.toString())
        if (!name) throw new RuntimeException("Not a valid URL input: $input")

        File f = new File(ctx.directory, name)
        try {
            FileUtils.copyURLToFile(url, f)
        }
        catch(e) {
            throw new RuntimeException("Not a valid URL input: $input", e)
        }

        ctx.set(input, f)
    }
}
