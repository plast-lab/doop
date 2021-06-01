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
		String analysisName = "context-insensitive"
		Analysis analysis = analyzeTest("invokedynamic-ForEach",
										['--platform', 'java_8'] + (wala ? ["--wala-fact-gen"] : []) + souffleInterpreter,
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
		List<String> opts = ['--platform', 'java_8', '--cache'] + testExports + souffleInterpreter
		Analysis analysis = analyzeTest("invokedynamic-FutureExample", opts, analysisName)

		then:
		varPointsToQ(analysis, '<java.util.concurrent.FutureTask: void run()>/l1#_261', '<example_foreach.FutureExample: void useFuture()>/invokedynamic_metafactory::call/0::: java.util.concurrent.Callable::: (Mock)::: reference example_foreach.FutureExample::lambda$useFuture$0 from <example_foreach.FutureExample: java.lang.Integer lambda$useFuture$0()> wrapped as java.util.concurrent.Callable.call')
		methodIsReachable(analysis, '<example_foreach.FutureExample: java.lang.Integer doComputation()>')

		where:
		analysisName << [ "context-insensitive", "1-object-sensitive+heap" ]
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test FutureExample2"() {
		when:
		Analysis analysis = analyzeTest("invokedynamic-FutureExample2", ["--platform", "java_8"] + souffleInterpreter, "2-object-sensitive+heap")

		then:
		methodIsReachable(analysis, '<example.Computation: java.lang.Integer computation()>')
	}
}
