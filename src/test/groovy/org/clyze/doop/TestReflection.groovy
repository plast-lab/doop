package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

class TestReflection extends ServerAnalysisTests {
	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 016 (reflection)"() {
		when:
		analyzeTest("016-reflection", ["--reflection-classic", "--reflection-dynamic-proxies",
									   "--gen-opt-directives", "--Xserver-logic",
									   "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
									   "--platform", "java_8"])

		then:
		varPointsToQ(analysis, '<Main: void main(java.lang.String[])>/cA#_31', '<class A>')
		varPointsToQ(analysis, '<Main: void main(java.lang.String[])>/cA_2#_39', '<class A>')
		varPointsToQ(analysis, '<Main: void main(java.lang.String[])>/constr#_32', '<<reified constructor <A: void <init>()>>>')
		varPointsToQ(analysis, '<Main: void main(java.lang.String[])>/constr2#_40', '<<reified constructor <A: void <init>(java.lang.Integer,B)>>>')
		varPointsToQ(analysis, '<Main: void main(java.lang.String[])>/a3#_33', '<reflective Class.newInstance/new A>')
		varPointsToQ(analysis, '<Main: void main(java.lang.String[])>/a3_2#_35', '<reflective Constructor.newInstance/new A>')
		varPointsToQ(analysis, '<Main: void main(java.lang.String[])>/iField#_44', '<<reified field <A: java.lang.Integer i>>>')
		varPointsToQ(analysis, '<Main: void main(java.lang.String[])>/bFieldVal#_50', '<A: void <init>()>/new B/0')
		varPointsToQ(analysis, '<Main: void main(java.lang.String[])>/bFieldValB#_51', '<A: void <init>()>/new B/0')
		varPointsToQ(analysis, '<Main: void testProxies()>/g#_107', '<proxy object for interface G at <Main: void testProxies()>/java.lang.reflect.Proxy.newProxyInstance/0>')
		proxyCGE(analysis, '<Main: void testProxies()>/G.countInteger/0', '<DHandler: java.lang.Object invoke(java.lang.Object,java.lang.reflect.Method,java.lang.Object[])>')
	}
}
