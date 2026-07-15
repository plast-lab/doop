package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Characterization tests for the -i / -l input-flag semantics, verified at the
 * fact-generation level (--facts-only). They lock the CURRENT behavior.
 *
 * Inputs live in src/test/resources/input-flags/ (prebuilt jars + their sources
 * and build.sh). Two versions of com.foo.Bar (markerV1/markerV2), a library
 * closure com.foo.{Baz,Deep,Refl}, and Mains that reference them in different
 * ways. Assertions read the generated *.facts directly.
 *
 * NOTE: relatively heavy (~10 min) — it runs 10 separate --facts-only fact
 * generations (each a full Soot pass). If this becomes a burden in routine CI,
 * consider annotating it (or the feature methods) with @spock.lang.Ignore and
 * running it on demand.
 */
class TestInputFlags extends DoopSpec {

	static String jar(String name) {
		TestInputFlags.class.getResource("/input-flags/${name}").file
	}

	private Analysis factsOnly(String id, List<String> io) {
		List<String> args = ['-a', 'context-insensitive', '--id', id,
							  '--platform', 'java_17', '--regex', 'com.foo.*:Main',
							  '--facts-only', '-Ldebug'] + io
		Main.main2(args as String[])
		return Main.analysis
	}

	// (1) Duplicate class name resolves by classpath order: first entry wins,
	//     -i before -l, CLI order breaking ties within a tier.
	@Unroll
	def "duplicate com.foo.Bar resolves to #expected (#id)"() {
		when:
		Analysis a = factsOnly(id, io)

		then:
		factsContain(a, "Method", "marker${expected}")
		factsMissing(a, "Method", "marker${expected == 'V1' ? 'V2' : 'V1'}")

		where:
		id      | io                                                                     | expected
		'if-a1' | ['-i', jar('barV1.jar'), '-i', jar('barV2.jar'), '-i', jar('main.jar')] | 'V1'
		'if-a2' | ['-i', jar('barV2.jar'), '-i', jar('barV1.jar'), '-i', jar('main.jar')] | 'V2'
		'if-b1' | ['-i', jar('main.jar'), '-l', jar('barV1.jar'), '-l', jar('barV2.jar')] | 'V1'
		'if-b2' | ['-i', jar('main.jar'), '-l', jar('barV2.jar'), '-l', jar('barV1.jar')] | 'V2'
		'if-c1' | ['-i', jar('main.jar'), '-i', jar('barV1.jar'), '-l', jar('barV2.jar')] | 'V1'
		'if-c2' | ['-i', jar('main.jar'), '-i', jar('barV2.jar'), '-l', jar('barV1.jar')] | 'V2'
	}

	// (2) Application scope is (-i inputs) ∩ regex, NOT the regex alone: a
	//     regex-matching class present only in -l is fact-generated but not
	//     labelled ApplicationClass.
	def "ApplicationClass is (-i) intersect regex, not the regex alone"() {
		when:
		Analysis lOnly = factsOnly('if-app-l', ['-i', jar('main.jar'), '-l', jar('barV1.jar')])
		Analysis iCase = factsOnly('if-app-i', ['-i', jar('main.jar'), '-i', jar('barV1.jar')])

		then:
		// Bar (in -l, referenced by main) is fact-generated ...
		factsContain(lOnly, "Method", "markerV1")
		// ... but is NOT application, though the regex com.foo.* matches it.
		factsMissing(lOnly, "ApplicationClass", "com.foo.Bar")
		// The same class given as -i IS application.
		factsContain(iCase, "ApplicationClass", "com.foo.Bar")
	}

	// (3) Resolution depth: a type-only symbolic reference to a -l class pulls
	//     it to full bodies transitively; a reflection-only reference does not
	//     load it at all.
	def "type-only -l ref pulls bodies transitively; reflection-only ref does not load the class"() {
		when:
		Analysis t = factsOnly('if-typeref', ['-i', jar('main-type.jar'),    '-l', jar('libdeep.jar')])
		Analysis r = factsOnly('if-reflect', ['-i', jar('main-reflect.jar'), '-l', jar('libdeep.jar')])

		then:
		// Baz referenced only as a type -> full bodies (whoAmI body string) ...
		factsContain(t, "Method", "<com.foo.Baz:")
		factsContain(t, "StringConstant", "Baz-BODY")
		// ... and Soot followed Baz's body reference down to Deep (also bodies).
		factsContain(t, "Method", "<com.foo.Deep:")
		factsContain(t, "StringConstant", "Deep-BODY")

		// Refl referenced only via a Class.forName string -> class NOT loaded:
		// the name is a StringConstant, but no Refl methods are generated.
		factsContain(r, "StringConstant", "com.foo.Refl")
		factsMissing(r, "Method", "<com.foo.Refl:")
	}
}
