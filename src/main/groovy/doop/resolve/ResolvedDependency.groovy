package doop.resolve

/**
 * A resolved dependency (a local file).
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 24/10/2014
 */
class ResolvedDependency implements Dependency {
    private final File file

    ResolvedDependency(File file) {
        this.file = file
    }

    @Override
    String dependency() {
        return file.toString()
    }

    @Override
    File resolve() {
        return file
    }
}
