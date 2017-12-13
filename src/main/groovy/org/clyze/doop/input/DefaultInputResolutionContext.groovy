package org.clyze.doop.input

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * The default implementation of the input resolution mechanism.
 */
class DefaultInputResolutionContext implements InputResolutionContext {

    protected Log logger = LogFactory.getLog(getClass())

    static final ChainResolver DEFAULT_RESOLVER = new ChainResolver(
        //the order matters
        new FileResolver(),
        new DirectoryResolver(),
        new URLResolver(),
        IvyResolver.newInstance()
    )

    //Always true, but this may change in the future
    boolean transitive = true

    List<String> inputs = new LinkedList<>()
    List<String> libraries = new LinkedList<>()

    //The setInput of resolved inputFiles
    protected final Map<String, ResolvedInput> resolvedInputs = new LinkedHashMap<>()
    protected final Map<String, ResolvedInput> resolvedLibraries = new LinkedHashMap<>()

    //the input resolver
    protected final ChainResolver resolver

    DefaultInputResolutionContext() {
        this(DEFAULT_RESOLVER)
    }

    DefaultInputResolutionContext(ChainResolver resolver) {
        this.resolver = resolver
    }

    @Override
    void add(String input, boolean isLib) {
        isLib? libraries.add(input) : inputs.add(input)
    }

    @Override
    void add(List<String> inputs, boolean isLib) {
        isLib? this.libraries.addAll(inputs) : this.inputs.addAll(inputs)
    }

    @Override
    void set(String input, File file, boolean isLib) {
        isLib? resolvedLibraries.put(input, new ResolvedInput(input, file)): resolvedInputs.put(input, new ResolvedInput(input, file))
    }

    @Override
    void set(String input, List<File> files, boolean isLib) {
        isLib? resolvedLibraries.put(input, new ResolvedInput(input, files)) :resolvedInputs.put(input, new ResolvedInput(input, files))
    }

    @Override
    Set<File> get(String input, boolean isLib) {
        ResolvedInput resolvedInput
        resolvedInput = isLib?  resolvedLibraries.get(input) :  resolvedInputs.get(input)
        return resolvedInput ? resolvedInput.files() : Collections.emptySet()
    }

    @Override
    void resolve() {
        inputs.each { input ->
            ResolvedInput resolvedInput = resolvedInputs.get(input)
            if (!resolvedInput)
                resolver.resolve(input, this, false)
        }

        libraries.each { input ->
            ResolvedInput resolvedInput = resolvedLibraries.get(input)
            if (!resolvedInput)
                resolver.resolve(input, this, true)
        }
    }


    @Override
    List<File> getAllInputs() {
        def allFiles = new LinkedList<File>()
        inputs.each { input ->
            logger.debug "Getting input $input"
            ResolvedInput resolvedInput = resolvedInputs.get(input)
            if (resolvedInput) {
                logger.debug "Input $input is resolved -> ${resolvedInput.files()}"
                allFiles.addAll(resolvedInput.files())
            }
            else {
                throw new RuntimeException("Unresolved input: $input")
            }
        }

        return allFiles
    }

    @Override
    List<File> getAllLibraries() {
        def allLibraryFiles = new LinkedList<File>()
        libraries.each { library ->
            logger.debug "Getting library $library"
            ResolvedInput resolvedLibrary = resolvedLibraries.get(library)
            if (resolvedLibrary) {
                logger.debug "Library $library is resolved -> ${resolvedLibrary.files()}"
                allLibraryFiles.addAll(resolvedLibrary.files())
            }
            else {
                throw new RuntimeException("Unresolved library: $library")
            }
        }

        return allLibraryFiles
    }

    @Override
    List<File> getAll() {
        List<File> all = new LinkedList<>()
        all.addAll(getAllInputs())
        all.addAll(getAllLibraries())

        return all
    }

    @Override
    List<String> inputs() {
        return inputs
    }

    @Override
    List<String> libraries() {
        return libraries
    }


    @Override
    String toString() {
        return "Inputs: ${inputs()} Libraries: ${libraries()}"
    }
}
