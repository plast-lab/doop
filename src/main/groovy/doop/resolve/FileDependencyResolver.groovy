package doop.resolve

import doop.core.Analysis
import doop.core.Helper

/**
 * A resolver that treats dependencies as local files.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 22/10/2014
 */
class FileDependencyResolver implements DependencyResolver {

    @Override
    File resolve(String dependency, Analysis analysis) {
        return Helper.checkFileOrThrowException(dependency, "Not a valid file dependency: $dependency")
    }
}
