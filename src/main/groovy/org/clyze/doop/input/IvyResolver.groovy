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

/**
 * Resolves the input as an Apache Ivy module descriptor that is downloaded using the default ivy settings.
 */
class IvyResolver implements InputResolver {

	private static final String[] ARTIFACT_TYPES = ["jar"]

	private final Ivy ivy

	private IvyResolver(Ivy ivy) {
		this.ivy = ivy
	}

	String name() { "ivy" }

	void resolve(String input, InputResolutionContext ctx, InputType inputType) {
		try {
			//Create temp ivy.xml file
			def ivyfile = File.createTempFile('ivy', '.xml')
			ivyfile.deleteOnExit()

			def dep = input.split(":")

			DefaultModuleDescriptor md = DefaultModuleDescriptor.newDefaultInstance(
					ModuleRevisionId.newInstance(dep[0], dep[1] + '-caller', 'working')
			)

			DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(
					md, ModuleRevisionId.newInstance(dep[0], dep[1], dep[2]), false, false, ctx.transitive
			)
			md.addDependency(dd)

			XmlModuleDescriptorWriter.write(md, ivyfile)

			String[] confs = ['default']
			ResolveOptions resolveOptions = new ResolveOptions().
					setConfs(confs).
					setTransitive(ctx.transitive).
					setArtifactFilter(FilterHelper.getArtifactTypeFilter(ARTIFACT_TYPES))
			ResolveReport report = ivy.resolve(ivyfile.toURI().toURL(), resolveOptions)

			/*
			 * The resolution of the input has side-effects, as it causes the addition of new library dependencies
			 * to the existing context.
			 * TODO: Verify correctness in all cases of -i, -l variations.
			 */
			File resolvedInput = null
			List<File> resolvedInputDependencies = []

			report.getAllArtifactsReports().eachWithIndex { ArtifactDownloadReport rpt, int index ->
				//Don't copy the files, just return them
				def f = rpt.localFile
				if (index == 0) {
					resolvedInput = f
				} else {
					resolvedInputDependencies.add(f)
				}
			}

			if (inputType == InputType.LIBRARY) {
				List<File> libs = [resolvedInput] + resolvedInputDependencies
				ctx.set(input, libs, InputType.LIBRARY)
			} else if (inputType == InputType.INPUT) {
				ctx.set(input, resolvedInput, InputType.INPUT)
				ctx.add(input, InputType.LIBRARY) //add the same input as library dependency too
				ctx.set(input, resolvedInputDependencies, InputType.LIBRARY)
			} else
				throw new RuntimeException("Ivy resolution is not supported for HeapDL inputs.")
		} catch (e) {
			throw new RuntimeException("Not a valid Ivy input: $input", e)
		}
	}

	static File copy(ArtifactDownloadReport report, File outDir) {
		File f = report.getLocalFile()
		File dest = new File(outDir, f.getName())
		FileUtils.copyFile(f, dest)
		return dest
	}

	static IvyResolver newInstance() {
		/*
		//Configure ivy with custom settings for Maven Central
		IvySettings settings = new IvySettings()
		URLResolver resolver = new URLResolver()
		resolver.setM2compatible(true)
		resolver.setName('central')
		//Central maven repo
		resolver.addArtifactPattern('http://repo1.maven.org/maven2/[organisation]/[module]/[revision]/[artifact](-[revision]).[ext]')
		settings.addResolver(resolver)
		settings.setDefaultResolver(resolver.getName())
		Ivy ivy = Ivy.getInstance(settings)
		*/

		//Configure ivy using the default settings
		def ivy = Ivy.newInstance()
		ivy.configureDefault()

		//and then add custom settings for Maven Central
		org.apache.ivy.plugins.resolver.URLResolver resolver = new org.apache.ivy.plugins.resolver.URLResolver()
		resolver.setM2compatible(true)
		resolver.setName('central')
		//Central maven repo
		resolver.addArtifactPattern('http://repo1.maven.org/maven2/[organisation]/[module]/[revision]/[artifact](-[revision]).[ext]')
		ivy.getSettings().addResolver(resolver)

		return new IvyResolver(ivy)
	}
}
