package doop.resolve

/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 8/12/2014
 *
 * A dependency to an existing (already resolved) file.
 */
class ExistingFileDependency implements Dependency {

    private final File f

    ExistingFileDependency(File f) {
        this.f = f
    }

    @Override
    String dependency() {
        return f.toString()
    }

    @Override
    File resolve() {
        return f
    }

    @Override
    String toString() {
        return dependency()
    }
}
