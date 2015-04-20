package doop.resolve

import doop.core.Analysis

/**
 * A jar dependency that is expressed as a String.
 * It can be resolved from:
 * <ul>
 *     <li>a local file</li>
 *     <li>a remote URL</li>
 *     <li>an Ivy dependency<li>
 * </ul>
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 22/10/2014
 */
class StringDependency implements Dependency {

    private File localFile

    String dependency
    Analysis analysis
    ChainDependencyResolver resolver = new ChainDependencyResolver(new FileDependencyResolver(),
                                                                   new URLDependencyResolver(),
                                                                   new IvyDependencyResolver())

    StringDependency(String dependency, Analysis analysis) {
        this.dependency = dependency
        this.analysis = analysis
    }

    //NOTE: Not thread-safe
    File resolve() {
        if (!localFile) {
            localFile = resolver.resolve(dependency, analysis)
        }
        return localFile
    }

    @Override
    String dependency() {
        return dependency
    }

    @Override
    String toString() {
        return dependency
    }
}
