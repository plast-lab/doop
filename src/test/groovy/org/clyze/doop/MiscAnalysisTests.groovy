package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test programs from the server-analysis-tests repo.
 */
class MiscAnalysisTests extends ServerAnalysisTests {

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test DefaultImplementation"() {
		when:
		Analysis analysis = analyzeTest("doop-bug-report-2018-07-20-DefaultImplementation", [])

		then:
		methodIsReachable(analysis, '<Foo: void meh()>')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test Flatten"() {
		when:
		Analysis analysis = analyzeTest("doop-bug-report-2018-07-20-Flatten", ["--reflection-classic"] + souffleInterpreter)

		then:
		methodIsReachable(analysis, '<Flatten: void flatten()>')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test ForkJoinProblem"() {
		when:
		Analysis analysis = analyzeTest("doop-bug-report-2018-07-20-ForkJoinProblem", ["--platform", "java_8"])

		then:
		methodIsReachable(analysis, '<ForkJoinProblem$Something: void compute()>')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test ListCompare"() {
		when:
		Analysis analysis = analyzeTest("doop-bug-report-2018-07-20-ListCompare", ["--platform", "java_8"])

		then:
		methodIsReachable(analysis, '<ListCompare$1: int compare(java.lang.Integer,java.lang.Integer)>')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test PQueueCompare"() {
		when:
		Analysis analysis = analyzeTest("doop-bug-report-2018-07-20-PQueueCompare", ["--platform", "java_8"])

		then:
		methodIsReachable(analysis, '<Compare: int compare(java.lang.Object,java.lang.Object)>')
		// Also check that an "unresolved compilation error" did not
		// affect the generation of the PriorityQueue constructor.
		assert false == find(analysis, "StringRaw", 'Unresolved compilation error: Method <java.util.PriorityQueue: void <init>(java.util.Comparator)> does not exist', false)
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test TimeProblem"() {
		when:
		Analysis analysis = analyzeTest("doop-bug-report-2018-07-20-TimeProblem", [])

		then:
		methodIsReachable(analysis, '<TimeProblem: void printTime(java.time.LocalTime)>')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 018-threads"(def mode) {
		when:
		Analysis analysis = analyzeTest("018-threads", ["--generate-jimple"] + mode)

		then:
		methodIsReachable(analysis, '<Main$1: void run()>')
		methodIsReachable(analysis, '<Main$2: void run()>')
		methodIsReachable(analysis, '<Main: void lambda$main$0()>')

        where:
        mode << [[], ["--Xisolate-fact-generation"]]
	}
}
