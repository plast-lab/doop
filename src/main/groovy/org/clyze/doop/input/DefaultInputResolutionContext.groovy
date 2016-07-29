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

    //The set of resolved inputFiles
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
        resolvedInputs.put(input, null)
    }

    @Override
    void add(List<String> inputs) {
        inputs.each {String input -> add(input)}
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
        if (resolvedInput) {
            return resolvedInput.files()
        }
        else {
            return Collections.emptySet()
        }
    }

    @Override
    void resolve() {
        for (String input: inputs()) {
            ResolvedInput resolvedInput = resolvedInputs.get(input)
            if (!resolvedInput) {
                resolver.resolve(input, this)
            }
        }
    }

    @Override
    List<File> getAll() {
        Set<File> allFiles = new LinkedHashSet<>()
        for (String input: inputs()) {
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

        return allFiles as List
    }

    @Override
    Set<String> inputs() {
        return Collections.unmodifiableSet(resolvedInputs.keySet())
    }

    @Override
    String toString() {
        return "Inputs: ${inputs()}"
    }
}
