package org.clyze.doop.input

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.analysis.InputType

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
    List<String> hprofs = new LinkedList<>()

    //The set of resolved inputFiles
    protected final Map<String, ResolvedInput> resolvedInputs = new LinkedHashMap<>()
    protected final Map<String, ResolvedInput> resolvedLibraries = new LinkedHashMap<>()
    protected final Map<String, ResolvedInput> resolvedHprofs = new LinkedHashMap<>()

    //the input resolver
    protected final ChainResolver resolver

    DefaultInputResolutionContext() {
        this(DEFAULT_RESOLVER)
    }

    DefaultInputResolutionContext(ChainResolver resolver) {
        this.resolver = resolver
    }

    List targetList(InputType inputType) {
        switch (inputType) {
        case InputType.INPUT  : return this.inputs
        case InputType.LIBRARY: return this.libraries
        case InputType.HPROF  : return this.hprofs
        default: throw new RuntimeException("Unknown inputType ${inputType}")
        }
    }

    Map<String, ResolvedInput> targetResolvedMap(InputType inputType) {
        switch (inputType) {
        case InputType.INPUT  : return this.resolvedInputs
        case InputType.LIBRARY: return this.resolvedLibraries
        case InputType.HPROF  : return this.resolvedHprofs
        default: throw new RuntimeException("Unknown inputType ${inputType}")
        }
    }

    @Override
    void add(String input, InputType inputType) {
        targetList(inputType).add(input)
    }

    @Override
    void add(List<String> inputs, InputType inputType) {
        targetList(inputType).addAll(inputs)
    }

    @Override
    void set(String input, File file, InputType inputType) {
        targetResolvedMap(inputType).put(input, new ResolvedInput(input, file))
    }

    @Override
    void set(String input, List<File> files, InputType inputType) {
        targetResolvedMap(inputType).put(input, new ResolvedInput(input, files))
    }

    @Override
    Set<File> get(String input, InputType inputType) {
        ResolvedInput resolvedInput = targetResolvedMap(inputType).get(input)
        return resolvedInput ? resolvedInput.files() : Collections.emptySet()
    }

    @Override
    void resolve() {
        inputs.each { input ->
            logger.debug "Resolving input $input"
            ResolvedInput resolvedInput = resolvedInputs.get(input)
            if (!resolvedInput)
                resolver.resolve(input, this, InputType.INPUT)
        }

        libraries.each { input ->
            logger.debug "Resolving library $input"
            ResolvedInput resolvedInput = resolvedLibraries.get(input)
            if (!resolvedInput)
                resolver.resolve(input, this, InputType.LIBRARY)
        }

        hprofs.each { input ->
            logger.debug "Resolving HPROF $input"
            ResolvedInput resolvedInput = resolvedHprofs.get(input)
            if (!resolvedInput)
                resolver.resolve(input, this, InputType.HPROF)
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
    List<File> getAllHprofs() {
        def allHprofFiles = new LinkedList<File>()
        hprofs.each { hprof ->
            logger.debug "Getting HPROF file ${hprof}"
            ResolvedInput resolvedHprof = resolvedHprofs.get(hprof)
            if (resolvedHprof) {
                logger.debug "HPROF file ${hprof} is resolved -> ${resolvedHprof.files()}"
                allHprofFiles.addAll(resolvedHprof.files())
            }
            else {
                throw new RuntimeException("Unresolved HPROF file: ${hprof}")
            }
        }

        return allHprofFiles
    }

    @Override
    List<File> getAll() {
        List<File> all = new LinkedList<>()
        all.addAll(getAllInputs())
        all.addAll(getAllLibraries())

        return all
    }

    @Override
    List<String> inputs() { inputs }

    @Override
    List<String> libraries() { libraries }

    @Override
    List<String> hprofs() { hprofs }

    @Override
    String toString() { "Inputs: ${inputs()} Libraries: ${libraries()}" }
}
