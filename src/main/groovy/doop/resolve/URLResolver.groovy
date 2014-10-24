package doop.resolve

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
/**
 * A resolver that treats dependencies as URLs.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 22/10/2014
 */
class URLResolver implements Resolver {

    @Override
    File resolve(String dependency, ResolutionContext ctx) {
        URL url
        try {
            url = new URL(dependency)
        }
        catch(e) {
            throw new RuntimeException("Not a valid URL dependency: $dependency", e)
        }


        String name = FilenameUtils.getName(url.toString())
        if (!name) throw new RuntimeException("Not a valid URL dependency: $dependency")

        File f = new File(ctx.baseDir, name)
        try {
            FileUtils.copyURLToFile(url, f)
        }
        catch(e) {
            throw new RuntimeException("Not a valid URL dependency: $dependency", e)
        }

        return f
    }
}
