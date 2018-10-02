package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test programs from the server-analysis-tests repo.
 */
class ServerAnalysisTests extends Specification {

	final static String SERVER_ANALYSIS_TESTS_DIR = "SERVER_ANALYSIS_TESTS_DIR"
	static String serverAnalysisTestsDir
	Analysis analysis

	def setupSpec() {
		Doop.initDoopFromEnv()

		serverAnalysisTestsDir = System.getenv(SERVER_ANALYSIS_TESTS_DIR)
		if (!serverAnalysisTestsDir) {
			System.err.println("Error: environment variable ${SERVER_ANALYSIS_TESTS_DIR} not set, cannot run server analysis tests")
		}
		assert null != serverAnalysisTestsDir
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 009 (native code)"() {
		when:
		analyzeTest("009-native", ["--simulate-native-returns"])

		then:
		varPointsTo(analysis, '<HelloJNI: void main(java.lang.String[])>/obj#_34', '<native java.lang.Object value allocated in <HelloJNI: java.lang.Object newJNIObj()>>')
		varPointsTo(analysis, '<HelloJNI: void main(java.lang.String[])>/list#_43', '<java.io.UnixFileSystem: java.lang.String[] list(java.io.File)>/new java.lang.String[]/0')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 012 (interface fields)"() {
		when:
		analyzeTest("012-interface-fields", ["--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl"])

		then:
		staticFieldPointsTo(analysis, '<Y: Y fooooooooo>', '<Y: void <clinit>()>/new Y$1/0')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 016 (reflection)"() {
		when:
		analyzeTest("016-reflection", ["--reflection-classic"])

		then:
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/cA#_27', '<class A>')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/cA_2#_35', '<class A>')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/constr#_28', '<<reified constructor <A: void <init>()>>>')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/constr2#_36', '<<reified constructor <A: void <init>(java.lang.Integer,B)>>>')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/a3#_29', '<reflective Class.newInstance/new A>')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/a3_2#_31', '<reflective Constructor.newInstance/new A>')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/iField#_40', '<<reified field <A: java.lang.Integer i>>>')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/bFieldVal#_46', '<A: void <init>()>/new B/0')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/bFieldValB#_47', '<A: void <init>()>/new B/0')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test DefaultImplementation"() {
		when:
		analyzeTest("doop-bug-report-2018-07-20-DefaultImplementation", [])

		then:
		methodIsReachable(analysis, '<Foo: void meh()>')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test Flatten"() {
		when:
		analyzeTest("doop-bug-report-2018-07-20-Flatten", ["--reflection-classic"])

		then:
		methodIsReachable(analysis, '<Flatten: void flatten()>')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test ForkJoinProblem"() {
		when:
		analyzeTest("doop-bug-report-2018-07-20-ForkJoinProblem", [])

		then:
		methodIsReachable(analysis, '<ForkJoinProblem$Something: void compute()>')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test ListCompare"() {
		when:
		analyzeTest("doop-bug-report-2018-07-20-ListCompare", [])

		then:
		methodIsReachable(analysis, '<ListCompare$1: int compare(java.lang.Integer,java.lang.Integer)>')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test PQueueCompare"() {
		when:
		analyzeTest("doop-bug-report-2018-07-20-PQueueCompare", [])

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
		analyzeTest("doop-bug-report-2018-07-20-TimeProblem", [])

		then:
		methodIsReachable(analysis, '<TimeProblem: void printTime(java.time.LocalTime)>')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 104 (method references)"() {
		when:
		analyzeTest("104-method-references", [])

		then:
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/c1#_15', '<java.lang.invoke.InnerClassLambdaMetafactory: java.lang.invoke.CallSite buildCallSite()>/new java.lang.invoke.ConstantCallSite/0')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/c1#_15', '<Main: void main(java.lang.String[])>/invokedynamic_A::meth1234/0::: java.util.function.Consumer::: (Mock)::: reference A::meth1234 from <A: void meth1234(java.lang.Integer)> wrapped as java.util.function.Consumer.accept')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/c3#_52', '<java.lang.invoke.InnerClassLambdaMetafactory: java.lang.invoke.CallSite buildCallSite()>/new java.lang.invoke.ConstantCallSite/0')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/c3#_52', '<Main: void main(java.lang.String[])>/invokedynamic_A::meth99/0::: java.util.function.Function::: (Mock)::: reference A::meth99 from <A: java.lang.Integer meth99(java.lang.Integer)> wrapped as java.util.function.Function.apply')
	}

	String analyzeTest(String test, List<String> extraArgs) {
		List args = ["-i", "${serverAnalysisTestsDir}/${test}/build/libs/${test}.jar",
					 "-a", "context-insensitive", // "--Xserver-logic",
					 "--id", "test-${test}", "--generate-jimple",
					 "--platform", "java_8", "--Xstats-full"] + extraArgs
		Main.main((String[])args)
		analysis = Main.analysis
	}
}
