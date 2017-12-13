package org.clyze.doop.input

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils

/**
 * Resolves the input as a URL.
 */
class URLResolver implements InputResolver{

    @Override
    String name() {
        return "url"
    }

    @Override
    void resolve(String input, InputResolutionContext ctx, boolean isLib) {
        try {
            URL url = new URL(input)
            File tmpFile = File.createTempFile(FilenameUtils.getBaseName(input) + "_", "." + FilenameUtils.getExtension(input))
            FileUtils.copyURLToFile(url, tmpFile)
            tmpFile.deleteOnExit()
            ctx.set(input, tmpFile, isLib)
        }
        catch(e) {
            throw new RuntimeException("Not a valid URL input: $input", e)
        }
    }
}
