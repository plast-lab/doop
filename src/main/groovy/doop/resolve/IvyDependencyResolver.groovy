package doop.resolve
import doop.core.Analysis
import org.apache.commons.io.FileUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorWriter
import org.apache.ivy.plugins.resolver.URLResolver
import org.apache.ivy.util.filter.FilterHelper

/**
 * A resolver that treats dependencies as Apache Ivy dependencies to be downloaded from Maven Central.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 26/10/2014
 */
class IvyDependencyResolver implements DependencyResolver {

    private static final String[] ARTIFACT_TYPES = ["jar"]
    private static final def ivy = configureIvy()


    Log logger = LogFactory.getLog(getClass())

    static Ivy configureIvy() {
        IvySettings settings = new IvySettings()
        URLResolver resolver = new URLResolver()
        resolver.setM2compatible(true)
        resolver.setName('central')
        //Central maven repo
        resolver.addArtifactPattern('http://repo1.maven.org/maven2/[organisation]/[module]/[revision]/[artifact](-[revision]).[ext]')
        settings.addResolver(resolver)
        settings.setDefaultResolver(resolver.getName())
        return Ivy.newInstance(settings)
    }

    @Override
    File resolve(String dependency, Analysis analysis) {

        try {

            String[] dep = dependency.split(":")

            File ivyfile = File.createTempFile('ivy', '.xml', new File(analysis.outDir))
            ivyfile.deleteOnExit()

            DefaultModuleDescriptor md = DefaultModuleDescriptor.newDefaultInstance(
                ModuleRevisionId.newInstance(dep[0], dep[1] + '-caller', 'working')
            )

            DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(
                md, ModuleRevisionId.newInstance(dep[0], dep[1], dep[2]), false, false, false
            )
            md.addDependency(dd)

            XmlModuleDescriptorWriter.write(md, ivyfile)

            String[] confs = ['default']
            ResolveOptions resolveOptions = new ResolveOptions().
                                            setConfs(confs).
                                            setArtifactFilter(FilterHelper.getArtifactTypeFilter(ARTIFACT_TYPES))
            ResolveReport report = ivy.resolve(ivyfile.toURI().toURL(), resolveOptions)

            File f = report.getAllArtifactsReports()[0].getLocalFile()
            File dest = new File(analysis.outDir, f.getName())
            FileUtils.copyFile(f, dest)
            return dest

        } catch (e) {
            throw new RuntimeException("Not a valid Ivy dependency: $dependency", e)
        }
    }
}
