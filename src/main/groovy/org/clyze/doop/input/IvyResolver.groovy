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
/**
 * Resolves the input as an Apache Ivy module descriptor that is downloaded using the default ivy settings.
 */
class IvyResolver implements InputResolver {

    private static final String[] ARTIFACT_TYPES = ["jar"]

    private final Ivy ivy

    private IvyResolver(Ivy ivy) {
        this.ivy = ivy
    }

    @Override
    String name() {
        return "ivy"
    }

    @Override
    void resolve(String input, InputResolutionContext ctx) {
        try {
            //Create temp ivy.xml file
            File ivyfile = File.createTempFile('ivy', '.xml')
            ivyfile.deleteOnExit()

            String[] dep = input.split(":")

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

            List<File> files = report.getAllArtifactsReports().collect { ArtifactDownloadReport rpt ->
                //Don't copy the files, just return them
                rpt.getLocalFile()
            }

            ctx.set(input, files)

        } catch (e) {
            throw new RuntimeException("Not a valid Ivy input: $input", e)
        }
    }

    private File copy(ArtifactDownloadReport report, File outDir) {
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
        Ivy ivy = Ivy.newInstance(settings)
        */

        //Configure ivy using the default settings
        Ivy ivy = Ivy.newInstance()
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
