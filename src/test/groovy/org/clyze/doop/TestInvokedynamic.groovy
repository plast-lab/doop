package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test handling of invokedynamic (method handles, method types, bootstrap methods).
 */
class TestInvokedynamic extends DoopSpec {
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
										 "--Xstats-none", "--no-standard-exports",
										 "--reflection-method-handles"] + reflectionOpts
		String analysisId = "test-115-invokedynamic" + (fullReflection ? "" : "-light-refl")
		Analysis analysis = analyzeBuiltinTest("115-invokedynamic",
											   options,
											   analysisName,
											   analysisId)

		then:
		// test1
		feature 'Lookup of a MethodHandles.Lookup object via MethodHandles.lookup().'
		testSucceeds(analysis, "test1.1")
		feature 'Construction of method type values via MethodType.methodType() methods.'
		testSucceeds(analysis, "test1.2")
		feature 'Inserting arguments in a method handle.'
		testSucceeds(analysis, "test1.3")
		feature 'Calling method handles with MethodHandle.invokeExact().'
		testSucceeds(analysis, "test1.4")
		testSucceeds(analysis, "test1.5")
		// test2
		feature 'Look-up of static methods via MethodHandles.Lookup.findStatic().'
		testSucceeds(analysis, "test2.1")
		testSucceeds(analysis, "test2.2")
		feature 'Calling static method handles with MethodHandle.invokeExact().'
		testSucceeds(analysis, "test2.3")
		// test3
		feature 'Look-up of virtual methods via MethodHandles.Lookup.findVirtual().'
		testSucceeds(analysis, "test3.1")
		feature 'Passing arguments.'
		testSucceeds(analysis, "test3.2")
		feature 'Passing a receiver for non-static methods.'
		testSucceeds(analysis, "test3.3")
		feature 'Calling non-static method handles with MethodHandle.invokeExact().'
		testSucceeds(analysis, "test3.4")
		// test4
		feature 'Support for method handles with non-void types and <methodType(Class, Class)>.'
		testSucceeds(analysis, "test4.1")
		testSucceeds(analysis, "test4.2")
		testSucceeds(analysis, "test4.3")
		// test5
		feature 'Support for <methodType(Class, Class[])>.'
		testSucceeds(analysis, "test5.1")
		testSucceeds(analysis, "test5.2")
		testSucceeds(analysis, "test5.3")
		// test6
		feature 'Support for <methodType(Class, Class, Class...)> and <methodType(Class, List)>.'
		testSucceeds(analysis, "test6.1")
		testSucceeds(analysis, "test6.2")
		testSucceeds(analysis, "test6.3")
		// test7
		feature 'Support for <methodType(Class, MethodType)>.'
		testSucceeds(analysis, "test7.1")
		testSucceeds(analysis, "test7.2")
		testSucceeds(analysis, "test7.3")
		// testInvokedynamic
		feature 'Bootstrap methods are made reachable.'
		testSucceeds(analysis, "testID.1")
		testSucceeds(analysis, "testID.2")
		testSucceeds(analysis, "testID.3")
		testSucceeds(analysis, "testID.4")
		feature 'Method handle call-graph edges created by bootstrap methods.'
		testSucceeds(analysis, "testID.5")
		testSucceeds(analysis, "testID.6")
		testSucceeds(analysis, "testID.7")
		feature 'Lookup objects in bootstrap methods.'
		testSucceeds(analysis, "testID.8")
		feature 'Boxing primitive values.'
		testSucceeds(analysis, "testID.9")

		where:
		fullReflection << [true, false]
	}
}
