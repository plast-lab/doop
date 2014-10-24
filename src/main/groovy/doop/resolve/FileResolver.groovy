package doop.resolve

import doop.Helper

/**
 * A resolver that treats dependencies as local files.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 22/10/2014
 */
class FileResolver implements Resolver {

    @Override
    File resolve(String dependency, ResolutionContext ctx) {
        return Helper.checkFileOrThrowException(dependency, "Not a valid file dependency: $dependency")
    }
}
