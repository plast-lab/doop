package doop.input

/**
 * A resolved input.
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 23/3/2015
 */
class ResolvedInput implements Input{
    private final String input
    private final Set<File> files

    private ResolvedInput(String input) {
        this.input = input
        this.files = new LinkedHashSet<>()
    }

    ResolvedInput(String input, File file) {
        this(input)
        this.files.add(file)
    }

    ResolvedInput(String input, List<File> files) {
        this(input)
        this.files.addAll(files)
    }

    @Override
    String name() {
        return input
    }

    @Override
    Set<File> files() {
        return files
    }

    @Override
    String toString() {
        return files.toString()
    }
}
