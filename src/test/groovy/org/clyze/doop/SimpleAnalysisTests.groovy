package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test small programs from the server-analysis-tests repo.
 */
class SimpleAnalysisTests extends ServerAnalysisTests {

	@spock.lang.Ignore
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
		analyzeTest("016-reflection", ["--reflection-classic", "--platform", "java_8"])

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
	def "Server analysis test 104 (method references)"() {
		when:
		analyzeTest("104-method-references", ["--platform", "java_8"])

		then:
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/c1#_15', '<java.lang.invoke.InnerClassLambdaMetafactory: java.lang.invoke.CallSite buildCallSite()>/new java.lang.invoke.ConstantCallSite/0')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/c1#_15', '<Main: void main(java.lang.String[])>/invokedynamic_A::meth1234/0::: java.util.function.Consumer::: (Mock)::: reference A::meth1234 from <A: void meth1234(java.lang.Integer)> wrapped as java.util.function.Consumer.accept')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/c3#_52', '<java.lang.invoke.InnerClassLambdaMetafactory: java.lang.invoke.CallSite buildCallSite()>/new java.lang.invoke.ConstantCallSite/0')
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/c3#_52', '<Main: void main(java.lang.String[])>/invokedynamic_A::meth99/0::: java.util.function.Function::: (Mock)::: reference A::meth99 from <A: java.lang.Integer meth99(java.lang.Integer)> wrapped as java.util.function.Function.apply')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 006 (hello world) / test additional command line options"() {
		when:
		analyzeTest("006-hello-world", ["--cfg", "--coarse-grained-allocation-sites", "--cs-library", "--sanity", "--Xextra-metrics", "--dont-report-phantoms", "--platform", "java_8", "--thorough-fact-gen"])

		then:
		relationHasExactSize(analysis, "VarHasNoType", 0)
		relationHasExactSize(analysis, "TypeIsNotConcreteType", 0)
		relationHasExactSize(analysis, "InstructionIsNotConcreteInstruction", 0)
		relationHasExactSize(analysis, "ValueHasNoType", 0)
		relationHasExactSize(analysis, "ValueHasNoDeclaringType", 0)
		relationHasExactSize(analysis, "NotReachableVarPointsTo", 0)
		relationHasExactSize(analysis, "VarPointsToWronglyTypedValue", 0)
		relationHasExactSize(analysis, "VarPointsToMergedHeap", 0)
		relationHasExactSize(analysis, "HeapAllocationHasNoType", 0)
		relationHasExactSize(analysis, "ValueIsNeitherHeapNorNonHeap", 0)
		relationHasExactSize(analysis, "ClassTypeIsInterfaceType", 0)
		relationHasExactSize(analysis, "PrimitiveTypeIsReferenceType", 0)
	}
}
