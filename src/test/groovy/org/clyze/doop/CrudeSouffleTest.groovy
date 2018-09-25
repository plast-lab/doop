package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Test Souffle analysis mode.
 */
class CrudeSouffleTest extends Specification {

	Analysis analysis

	@Unroll
	def "Crude testing Souffle mode (based on sample metrics similarity) using [#scenario]"() {
		when:
		def propertyFile = this.class.getResource("/scenarios/$scenario").file
		Main.main((String[])["--Xstats-full", "-p", propertyFile])
		analysis = Main.analysis

		then:
		equals("var points-to (SENS)", vpt)
		equals("instance field points-to (INS)", fpt)
		equals("call graph edges (INS)", cge)
		equals("polymorphic virtual call sites", pCalls)
		equals("reachable casts that may fail", fCasts)
		equals("app reachable casts", aRCasts)
		equals("reachable variables (INS)", rVarsINS)
		equals("reachable variables (SENS)", rVarsSENS)
		equals("reachable methods (INS)", rMethodsINS)
		equals("reachable methods (SENS)", rMethodsSENS)
		equals("app virtual call sites (statically)", aVCS)
		equals("app reachable virtual call sites", aRVCS)
		equals("non-reachable app concrete methods", nRACM)

		where:
		scenario                                  | vpt      | fpt     | cge    | pCalls | fCasts | aRCasts | rVarsINS | rVarsSENS | rMethodsINS | rMethodsSENS | aVCS    | aRVCS | nRACM
		"antlr-insensitive-tamiflex.properties"   | 2595458  | 271819  | 57869  | 1957   | 1123   | 322     | 89730    | 89730     | 8405        | 8405         | 19625   | 16855 | 964
		"antlr-1call-tamiflex.properties"         | 10458878 | 192361  | 55859  | 1887   | 954    | 322     | 89115    | 373940    | 8316        | 56017        | 19625   | 16855 | 964
		"antlr-1objH-tamiflex.properties"         | 6003045  | 104251  | 54545  | 1811   | 926    | 322     | 88348    | 371868    | 8224        | 56017        | 19625   | 16855 | 965
		"antlr-insensitive-reflection.properties" | 8376198  | 1111250 | 56606  | 1814   | 1456   | 15      | 97936    | 97936     | 10724       | 10724        | 19625   | 480   | 2328
	}

	void equals(String metric, long expectedVal) {
		long actualVal = -1

		String metrics = "${analysis.database}/Stats_Metrics.csv"
		(new File(metrics)).eachLine { line ->
			String[] values = line.split('\t')
			if ((values.size() == 3) && (values[1] == metric)) {
				actualVal = values[2] as long
			}
		}
		// We expect numbers to deviate by 10%.
		assert actualVal > (expectedVal * 0.9)
		assert actualVal < (expectedVal * 1.1)
	}
}
