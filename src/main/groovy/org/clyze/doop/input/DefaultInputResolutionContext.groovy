package org.clyze.doop.input

import groovy.util.logging.Log4j
import org.clyze.analysis.InputType

/**
 * The default implementation of the input resolution mechanism.
 */
@Log4j
class DefaultInputResolutionContext implements InputResolutionContext {

	static final ChainResolver DEFAULT_RESOLVER = new ChainResolver(
			// The order matters
			new FileResolver(),
			new DirectoryResolver(),
			new URLResolver(),
			IvyResolver.newInstance()
	)

	// The input resolver
	ChainResolver resolver

	// Always true, but this may change in the future
	boolean transitive = true

	Map<InputType, List<String>> files = [:].withDefault { [] }
	Map<InputType, Map<String, ResolvedInput>> resolvedFiles = [:].withDefault { [:] }

	DefaultInputResolutionContext(ChainResolver resolver = DEFAULT_RESOLVER) {
		this.resolver = resolver
	}

	@Override
	void add(String input, InputType inputType) { files[inputType] << input }

	@Override
	void add(List<String> inputs, InputType inputType) { files[inputType] += inputs }

	@Override
	void set(String input, File file, InputType inputType) {
		resolvedFiles[inputType][input] = new ResolvedInput(input, file)
	}

	@Override
	void set(String input, List<File> files, InputType inputType) {
		resolvedFiles[inputType][input] = new ResolvedInput(input, files)
	}

	@Override
	Set<File> get(String input, InputType inputType) { resolvedFiles[inputType][input]?.files() ?: [] as Set }

	@Override
	void resolve() {
		files.each { inputType, paths ->
			paths.each { path ->
				log.debug "Resolving $path ($inputType)"
				def resolvedFile = resolvedFiles[inputType][path]
				if (!resolvedFile)
					resolver.resolve(path, this, inputType)
			}
		}
	}

	@Override
	List<String> inputs() { files[InputType.INPUT] }

	@Override
	List<String> libraries() { files[InputType.LIBRARY] }

	@Override
	List<String> platformFiles() { files[InputType.PLATFORM] }

	@Override
	List<String> hprofs() { files[InputType.HPROF] }

	private List<File> get0(InputType inputType) {
		def resolvedList = []
		files[inputType].each { path ->
			log.debug "Getting $path ($inputType)"
			def resolved = resolvedFiles[inputType][path]
			if (resolved) {
				log.debug "$path ($inputType) is resolved -> ${resolved.files()}"
				resolvedList += resolved.files()
			} else {
				throw new RuntimeException("Unresolved $path ($inputType)")
			}
		}
		resolvedList
	}

	@Override
	List<File> getAllInputs() { get0(InputType.INPUT) }

	@Override
	List<File> getAllLibraries() { get0(InputType.LIBRARY) }

	@Override
	List<File> getAllPlatformFiles() { get0(InputType.PLATFORM) }

	@Override
	List<File> getAllHprofs() { get0(InputType.HPROF) }

	@Override
	List<File> getAll() { files.keySet().collect { inputType -> get0(inputType) }.flatten() as List<File> }

	@Override
	String toString() { files.collect { inputType, paths -> "$inputType: $paths" }.join(", ") }
}
