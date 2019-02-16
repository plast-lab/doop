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
	def "Server analysis test 107 (lambdas)"(String analysisName, boolean wala) {
		when:
		Analysis analysis = analyzeTest("107-lambdas",
										["--platform", "java_8", "--Xserver-logic",
										 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
										 "--thorough-fact-gen", "--sanity",
										 "--generate-jimple"] +
										(wala ? ["--wala-fact-gen"] : []),
										analysisName,
										"107-lambdas" + (wala ? "-wala" : ""))

		then:
		varPointsToQ(analysis,
					 (wala ? '<Main: void main(java.lang.String[])>/v14' : '<Main: void main(java.lang.String[])>/intWriter#_13'),
					 '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::apply/0::: java.util.function.Function::: (Mock)::: lambda object of type java.util.function.Function')
		linkObjectIsLambda(analysis, '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::apply/0::: java.util.function.Function::: (Mock)::: lambda object of type java.util.function.Function', '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::apply/0::: java.util.function.Function::: (Mock)::: reference Main::lambda$main$0 from <Main: java.lang.String lambda$main$0(java.lang.Integer)> wrapped as java.util.function.Function.apply')
		lambdaCGE(analysis, '<Main: void main(java.lang.String[])>/java.util.function.Function.apply/0', '<Main: java.lang.String lambda$main$0(java.lang.Integer)>')
		invoValue(analysis, '<A: java.lang.Integer lambda$new$0(java.lang.Integer,java.lang.Integer,java.lang.Integer)>/java.lang.Integer.compareTo/0', '<java.lang.Integer: int compareTo(java.lang.Integer)>')
		invoValue(analysis, '<A: java.lang.Integer lambda$new$0(java.lang.Integer,java.lang.Integer,java.lang.Integer)>/java.lang.Integer.compareTo/1', '<java.lang.Integer: int compareTo(java.lang.Integer)>')
		methodIsReachable(analysis, '<java.lang.invoke.InnerClassLambdaMetafactory: java.lang.invoke.CallSite buildCallSite()>')
		if (!wala)
			noSanityErrors(analysis)

		where:
		analysisName << TEST_ANALYSES
		wala << [true, false]
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
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/c1#_15', '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::accept/0::: java.util.function.Consumer::: (Mock)::: lambda object of type java.util.function.Consumer')
		linkObjectIsLambda(analysis, '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::accept/0::: java.util.function.Consumer::: (Mock)::: lambda object of type java.util.function.Consumer', '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::accept/0::: java.util.function.Consumer::: (Mock)::: reference A::meth1234 from <A: void meth1234(java.lang.Integer)> wrapped as java.util.function.Consumer.accept')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/c3#_52', '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::apply/2::: java.util.function.Function::: (Mock)::: lambda object of type java.util.function.Function')
		linkObjectIsLambda(analysis, '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::apply/2::: java.util.function.Function::: (Mock)::: lambda object of type java.util.function.Function', '<Main: void main(java.lang.String[])>/invokedynamic_metafactory::apply/2::: java.util.function.Function::: (Mock)::: reference A::meth99 from <A: java.lang.Integer meth99(java.lang.Integer)> wrapped as java.util.function.Function.apply')
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
		varPointsTo(analysis, '<MethodReferencesTest: java.util.Collection transferElements(java.util.Collection,java.util.function.Supplier)>/result#_52', 'mock java.util.HashSet constructed by constructor reference at <MethodReferencesTest: java.util.Collection transferElements(java.util.Collection,java.util.function.Supplier)>/java.util.function.Supplier.get/0')
		noSanityErrors(analysis)

		where:
		analysisName << TEST_ANALYSES
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 115 (invokedynamic, method handles/types)"(boolean fullReflection) {
		when:
		String analysisName = "context-insensitive";
		List reflectionOpts = fullReflection ?
							  ["--reflection-classic"] :
							  ["--light-reflection-glue", "--distinguish-all-string-constants"]
		List options = ["--platform", "java_8",
										 "--generate-jimple",
										 // "--Xserver-logic",
										 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/TestInvokedynamic.dl",
										 "--thorough-fact-gen", "--sanity",
										 "--Xstats-none",
										 "--reflection-method-handles"] + reflectionOpts
		String analysisId = "115-invokedynamic" + (fullReflection ? "" : "-light-refl")
		Analysis analysis = analyzeTest("115-invokedynamic",
										options,
										analysisName,
										analysisId)

		then:
		// test1
		testSucceeds(analysis, "test1.1")
		testSucceeds(analysis, "test1.2")
		testSucceeds(analysis, "test1.3")
		testSucceeds(analysis, "test1.4")
		testSucceeds(analysis, "test1.5")
		// test2
		testSucceeds(analysis, "test2.1")
		testSucceeds(analysis, "test2.2")
		testSucceeds(analysis, "test2.3")
		// test3
		testSucceeds(analysis, "test3.1")
		testSucceeds(analysis, "test3.2")
		testSucceeds(analysis, "test3.3")
		testSucceeds(analysis, "test3.4")
		// test4
		testSucceeds(analysis, "test4.1")
		testSucceeds(analysis, "test4.2")
		testSucceeds(analysis, "test4.3")
		// test5
		testSucceeds(analysis, "test5.1")
		testSucceeds(analysis, "test5.2")
		testSucceeds(analysis, "test5.3")
		// test6
		testSucceeds(analysis, "test6.1")
		testSucceeds(analysis, "test6.2")
		testSucceeds(analysis, "test6.3")
		// test7
		testSucceeds(analysis, "test7.1")
		testSucceeds(analysis, "test7.2")
		testSucceeds(analysis, "test7.3")
		// testInvokedynamic
		testSucceeds(analysis, "testID.1")
		testSucceeds(analysis, "testID.2")
		testSucceeds(analysis, "testID.3")
		testSucceeds(analysis, "testID.4")
		testSucceeds(analysis, "testID.5")
		testSucceeds(analysis, "testID.6")
		testSucceeds(analysis, "testID.7")
		testSucceeds(analysis, "testID.8")
		testSucceeds(analysis, "testID.9")

		where:
		fullReflection << [true, false]
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test ForEach"(boolean wala) {
		when:
		String analysisName = "context-insensitive";
		Analysis analysis = analyzeTest("invokedynamic-ForEach",
										["--platform", "java_8"] + (wala ? ["--wala-fact-gen"] : []),
										analysisName,
										"invokedynamic-ForEach" + (wala ? "-wala" : ""))

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
		analysisName << TEST_ANALYSES
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
