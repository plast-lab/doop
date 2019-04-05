package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test code using lambdas and method references.
 */
class MiscLambdaTests extends ServerAnalysisTests {
	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test ForEach"(boolean wala) {
		when:
		String analysisName = "context-insensitive";
		Analysis analysis = analyzeTest("invokedynamic-ForEach",
										["--platform", "java_8"] + (wala ? ["--wala-fact-gen"] : []),
										analysisName,
										"test-invokedynamic-ForEach" + (wala ? "-wala" : ""))

		then:
		methodIsReachable(analysis, '<example_foreach.ForEach: void printTheList()>')

		where:
		wala << [true, false]
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test FutureExample"(String analysisName) {
		when:
		Analysis analysis = analyzeTest("invokedynamic-FutureExample", ["--platform", "java_8"], analysisName)

		then:
		varPointsTo(analysis, '<java.util.concurrent.FutureTask: void run()>/l1#_261', '<example_foreach.FutureExample: void useFuture()>/invokedynamic_metafactory::call/0::: java.util.concurrent.Callable::: (Mock)::: lambda object of type java.util.concurrent.Callable')
		methodIsReachable(analysis, '<example_foreach.FutureExample: java.lang.Integer doComputation()>')

		where:
		analysisName << [ "context-insensitive", "1-object-sensitive+heap" ]
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test FutureExample2"() {
		when:
		Analysis analysis = analyzeTest("invokedynamic-FutureExample2", ["--platform", "java_8"], "2-object-sensitive+heap")

		then:
		methodIsReachable(analysis, '<example.Computation: java.lang.Integer computation()>')
	}
}
