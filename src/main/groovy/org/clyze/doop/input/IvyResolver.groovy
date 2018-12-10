package org.clyze.doop.input

import org.apache.commons.io.FileUtils
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ArtifactDownloadReport
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorWriter
import org.apache.ivy.util.filter.FilterHelper
import org.clyze.analysis.InputType
import org.clyze.fetcher.Artifact
import org.clyze.fetcher.ArtifactFetcher
import org.clyze.fetcher.IvyArtifactFetcher

/**
 * Resolves the input as an Apache Ivy module descriptor that is downloaded using the default ivy settings.
 */
class IvyResolver implements InputResolver {

	String name() { "ivy" }

    void resolve(String artifactId, InputResolutionContext ctx, InputType inputType) {
        ArtifactFetcher.Repo repo = ArtifactFetcher.Repo.JCENTER
        Artifact art = new IvyArtifactFetcher().fetch(artifactId, repo, true)

        def resolvedInput = art.jar
        println "resolvedInput = ${resolvedInput}"
        def resolvedInputDependencies = art.dependencies.collect { new File(it) }
        println "resolvedInputDependencies = ${resolvedInputDependencies}"
        println "inputType = ${inputType}"
        if (inputType == InputType.LIBRARY) {
            List<File> libs = [resolvedInput] + resolvedInputDependencies
            ctx.set(artifactId, libs, InputType.LIBRARY)
        } else if (inputType == InputType.INPUT) {
            ctx.set(artifactId, resolvedInput, InputType.INPUT)
            //ctx.add(artifactId, InputType.LIBRARY) //add the same input as library dependency too
            ctx.set(artifactId, resolvedInputDependencies, InputType.LIBRARY)
        } else {
            throw new RuntimeException("Ivy resolution is not supported for HeapDL inputs.")
        }

        art.cleanUp()
    }
}
