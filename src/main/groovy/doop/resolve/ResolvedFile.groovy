package doop.resolve

/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 24/10/2014
 */
class ResolvedFile implements Resolveable {
    private final File file

    ResolvedFile(File file) {
        this.file = file
    }

    @Override
    String subject() {
        return file.toString()
    }

    @Override
    File resolve() {
        return file
    }
}
