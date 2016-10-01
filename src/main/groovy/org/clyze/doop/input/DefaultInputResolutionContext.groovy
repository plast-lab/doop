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

    //The set of resolved inputs
    protected final Map<String, ResolvedInput> resolvedInputs = new LinkedHashMap<>()

    //the input resolver
    protected final ChainResolver resolver

    DefaultInputResolutionContext() {
        this(DEFAULT_RESOLVER)
    }

    DefaultInputResolutionContext(ChainResolver resolver) {
        this.resolver = resolver
    }

    @Override
    void add(String input) {
        inputs.add(input)
    }

    @Override
    void add(List<String> inputs) {
        this.inputs.addAll(inputs)
    }

    @Override
    void set(String input, File file) {
        resolvedInputs.put(input, new ResolvedInput(input, file))
    }

    @Override
    void set(String input, List<File> files) {
        resolvedInputs.put(input, new ResolvedInput(input, files))
    }

    @Override
    Set<File> get(String input) {
        ResolvedInput resolvedInput = resolvedInputs.get(input)
        return resolvedInput ? resolvedInput.files() : Collections.emptySet()
    }

    @Override
    void resolve() {
        inputs.each { input ->
            ResolvedInput resolvedInput = resolvedInputs.get(input)
            if (!resolvedInput)
                resolver.resolve(input, this)
        }
    }

    @Override
    List<File> getAll() {
        def allFiles = new LinkedList<File>()
        inputs.each { input ->
            logger.debug "Getting $input"
            ResolvedInput resolvedInput = resolvedInputs.get(input)
            if (resolvedInput) {
                logger.debug "The $input is resolved -> ${resolvedInput.files()}"
                allFiles.addAll(resolvedInput.files())
            }
            else {
                throw new RuntimeException("Unresolved input: $input")
            }
        }

        return allFiles
    }

    @Override
    List<String> inputs() {
        return inputs
    }

    @Override
    String toString() {
        return "Inputs: ${inputs()}"
    }
}
