package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test code using invokedynamic (lambdas, method handles, method
 * references).
 */
class TestInvokedynamic extends ServerAnalysisTests {
    final static String[] TEST_ANALYSES = [ "context-insensitive", "1-object-sensitive+heap" ]

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 107 (lambdas)"(String analysisName) {
		when:
		Analysis analysis = analyzeTest("107-lambdas",
										["--platform", "java_8", "--Xserver-logic",
										 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
										 "--thorough-fact-gen", "--sanity",
										 "--generate-jimple"],
										analysisName)

		then:
		varPointsToQ(analysis, '<Main: void main(java.lang.String[])>/intWriter#_13', '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::apply/0::: java.util.function.Function::: (Mock)::: link object of type java.util.function.Function')
		linkObjectIsLambda(analysis, '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::apply/0::: java.util.function.Function::: (Mock)::: link object of type java.util.function.Function', '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::apply/0::: java.util.function.Function::: (Mock)::: reference Main::lambda$main$0 from <Main: java.lang.String lambda$main$0(java.lang.Integer)> wrapped as java.util.function.Function.apply')
		lambdaCGE(analysis, '<Main: void main(java.lang.String[])>/java.util.function.Function.apply/0', '<Main: java.lang.String lambda$main$0(java.lang.Integer)>')
		invoValue(analysis, '<A: java.lang.Integer lambda$new$0(java.lang.Integer,java.lang.Integer,java.lang.Integer)>/java.lang.Integer.compareTo/0', '<java.lang.Integer: int compareTo(java.lang.Integer)>')
		invoValue(analysis, '<A: java.lang.Integer lambda$new$0(java.lang.Integer,java.lang.Integer,java.lang.Integer)>/java.lang.Integer.compareTo/1', '<java.lang.Integer: int compareTo(java.lang.Integer)>')
		methodIsReachable(analysis, '<java.lang.invoke.InnerClassLambdaMetafactory: java.lang.invoke.CallSite buildCallSite()>')
		noSanityErrors(analysis)

		where:
		analysisName << TEST_ANALYSES
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 104 (method references)"(String analysisName) {
		when:
		Analysis analysis = analyzeTest("104-method-references",
										["--platform", "java_8",
										 "--thorough-fact-gen", "--sanity",
										 "--generate-jimple",
										 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl"],
										analysisName)

		then:
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/c1#_15', '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::accept/0::: java.util.function.Consumer::: (Mock)::: link object of type java.util.function.Consumer')
		linkObjectIsLambda(analysis, '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::accept/0::: java.util.function.Consumer::: (Mock)::: link object of type java.util.function.Consumer', '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::accept/0::: java.util.function.Consumer::: (Mock)::: reference A::meth1234 from <A: void meth1234(java.lang.Integer)> wrapped as java.util.function.Consumer.accept')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/c3#_52', '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::apply/2::: java.util.function.Function::: (Mock)::: link object of type java.util.function.Function')
		linkObjectIsLambda(analysis, '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::apply/2::: java.util.function.Function::: (Mock)::: link object of type java.util.function.Function', '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::apply/2::: java.util.function.Function::: (Mock)::: reference A::meth99 from <A: java.lang.Integer meth99(java.lang.Integer)> wrapped as java.util.function.Function.apply')
		varPointsTo(analysis, '<A: void meth1234(java.lang.Integer)>/x#_0', '<Main: void main(java.lang.String[])>/new java.lang.Integer/0')
		varPointsTo(analysis, '<A: java.lang.Integer meth99(java.lang.Integer)>/this#_0', '<Main: void main(java.lang.String[])>/new A/0')
		lambdaCGE(analysis, '<Main: void main(java.lang.String[])>/java.util.function.Supplier.get/1', '<A: void <init>()>')
		varPointsTo(analysis, '<A: void <init>()>/@this', 'mock A constructed by constructor reference at <Main: void main(java.lang.String[])>/java.util.function.Supplier.get/1')
		// Test for static method reference (Person::compareByAge).
		varPointsTo(analysis, '<Person: int compareByAge(Person,Person)>/a#_0', '<Person: java.util.List createRoster()>/new Person/0')
		varPointsTo(analysis, '<Person: int compareByAge(Person,Person)>/a#_0', '<Person: java.util.List createRoster()>/new Person/1')
		varPointsTo(analysis, '<Person: int compareByAge(Person,Person)>/b#_0', '<Person: java.util.List createRoster()>/new Person/0')
		varPointsTo(analysis, '<Person: int compareByAge(Person,Person)>/b#_0', '<Person: java.util.List createRoster()>/new Person/1')
		// Test instance method reference that captures receiver (myComparisonProvider::compareByName).
		varPointsTo(analysis, '<MethodReferencesTest$1ComparisonProvider: int compareByName(Person,Person)>/this#_0', '<MethodReferencesTest: void main(java.lang.String[])>/new MethodReferencesTest$1ComparisonProvider/0')
		// Test instance method reference that does not capture receiver (String::compareToIgnoreCase).
		varPointsTo(analysis, '<java.lang.String: int compareToIgnoreCase(java.lang.String)>/l0#_0', '<MethodReferencesTest: void main(java.lang.String[])>/new java.lang.String/0')
		varPointsTo(analysis, '<java.lang.String: int compareToIgnoreCase(java.lang.String)>/l0#_0', '<MethodReferencesTest: void main(java.lang.String[])>/new java.lang.String/1')
		varPointsTo(analysis, '<java.lang.String: int compareToIgnoreCase(java.lang.String)>/l1#_0', '<MethodReferencesTest: void main(java.lang.String[])>/new java.lang.String/0')
		varPointsTo(analysis, '<java.lang.String: int compareToIgnoreCase(java.lang.String)>/l1#_0', '<MethodReferencesTest: void main(java.lang.String[])>/new java.lang.String/1')
		// Test constructor reference (HashSet::new).
		varPointsTo(analysis, '<MethodReferencesTest: java.util.Collection transferElements(java.util.Collection,java.util.function.Supplier)>/$r0', 'mock java.util.HashSet constructed by constructor reference at <MethodReferencesTest: java.util.Collection transferElements(java.util.Collection,java.util.function.Supplier)>/java.util.function.Supplier.get/0')
		noSanityErrors(analysis)

		where:
		analysisName << TEST_ANALYSES
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 115 (invokedynamic, method handles/types)"() {
		when:
		Analysis analysis = analyzeTest("115-invokedynamic",
										["--platform", "java_8",
										 "--generate-jimple",
										 // "--Xserver-logic",
										 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
										 "--thorough-fact-gen", "--sanity",
										 "--reflection-classic", "--reflection-method-handles"])

		then:
		varPointsTo(analysis, '<Main: void test1()>/println2out#_31', '<computed direct method handle for <java.io.PrintStream: void println(java.lang.String)>>')
		varPointsTo(analysis, '<Main: void test2()>/staticMeth_mh#_39', '<computed direct method handle for <A: java.lang.Integer staticMeth()>>')
		varPointsTo(analysis, '<Main: void test2()>/ret#_41', '<A: java.lang.Integer staticMeth()>/new java.lang.Integer/0')
		methodIsReachable(analysis, '<A: java.lang.Integer staticMeth()>')
		varPointsTo(analysis, '<Main: void test3()>/methI_mh#_50', '<computed direct method handle for <A: void methI(java.lang.Integer)>>')
		varPointsTo(analysis, '<A: void methI(java.lang.Integer)>/i#_0', '<Main: void test3()>/new java.lang.Integer/0')
		varPointsTo(analysis, '<A: void methI(java.lang.Integer)>/this#_0', '<Main: void test3()>/new A/0')
		methodIsReachable(analysis, '<A: void methI(java.lang.Integer)>')
		varPointsTo(analysis, '<Main: void test4()>/methDD_mh#_59', '<computed direct method handle for <A: java.lang.Double doubleIdentity(java.lang.Double)>>')
		methodIsReachable(analysis, '<A: java.lang.Double doubleIdentity(java.lang.Double)>')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test ForEach"() {
		when:
		Analysis analysis = analyzeTest("invokedynamic-ForEach", ["--platform", "java_8"])

		then:
		methodIsReachable(analysis, '<example_foreach.ForEach: void printTheList()>')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test FutureExample"(String analysisName) {
		when:
		Analysis analysis = analyzeTest("invokedynamic-FutureExample", ["--platform", "java_8" ], analysisName)

		then:
		varPointsTo(analysis, '<java.util.concurrent.FutureTask: void run()>/l1#_261', '<example_foreach.FutureExample: void useFuture()>/invokedynamic_metafactory::call/0::: java.util.concurrent.Callable::: (Mock)::: link object of type java.util.concurrent.Callable')
		methodIsReachable(analysis, '<example_foreach.FutureExample: java.lang.Integer doComputation()>')

		where:
		analysisName << TEST_ANALYSES
	}
}
