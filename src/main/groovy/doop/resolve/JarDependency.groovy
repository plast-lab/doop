package doop.resolve
/**
 * A jar dependency: a jar file that will ultimately reside in the local file system.
 *
 * Jar dependencies need to be resolved before being used.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 22/10/2014
 */
class JarDependency implements Resolveable {

    private File localFile

    String dependency
    ResolutionContext ctx
    ChainResolver resolver = new ChainResolver(new FileResolver(), new URLResolver())

    JarDependency(String dependency, File baseDir) {
        this.dependency = dependency
        ctx = new ResolutionContext(baseDir:baseDir)
    }

    //NOTE: Not thread-safe
    File resolve() {
        if (!localFile) {
            localFile = resolver.resolve(dependency, ctx)
        }
        return localFile
    }

    @Override
    String subject() {
        return dependency
    }

    @Override
    String toString() {
        return dependency
    }
}
