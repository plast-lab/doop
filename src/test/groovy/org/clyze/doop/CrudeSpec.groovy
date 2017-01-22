package org.clyze.doop

import org.clyze.doop.core.Analysis
import spock.lang.Specification
import spock.lang.Unroll

class CrudeSpec extends Specification {

	Analysis analysis

	@Unroll
	def "Crude testing based on sample metrics similarity"() {
		when:
		def propertyFile = getClass().getResource("/scenarios/$scenario").getFile().toString()
		Main.main((String[])["-p", propertyFile])
		analysis = Main.analysis

		then:
		equals("var points-to (SENS)", expVPT)
		equals("instance field points-to (INS)", expFPT)
		equals("call graph edges (INS)", expCGE)
		equals("polymorphic virtual call sites", expPolyCalls)
		equals("reachable casts that may fail", expFailCasts)

		where:
		scenario                                  | expVPT   | expFPT  | expCGE | expPolyCalls | expFailCasts
		"antlr-insensitive-tamiflex.properties"   | 2558505  | 272062  | 58659  | 1993         | 1139
		"antlr-1call-tamiflex.properties"         | 10606450 | 195205  | 56576  | 1914         | 891
		"antlr-1objH-tamiflex.properties"         | 6499007  | 108610  | 55054  | 1806         | 847
		"antlr-insensitive-reflection.properties" | 8461301  | 1029402 | 79650  | 2532         | 1679
	}

	void equals(String metric, int expectedVal) {
		int actualVal
		analysis.connector.processQuery("_(v) <- Stats:Metrics(_, \"$metric\", v).")
										{ line -> actualVal = line as int }
		assert actualVal == expectedVal
	}
}
