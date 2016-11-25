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
		equals("01a@ var points-to (INS)", expVPT)
		equals("03a@ instance field points-to (INS)", expFPT)
		equals("08a@ call graph edges (INS)", expCGE)
		equals("14@ polymorphic virtual call sites", expPolyCalls)
		equals("22@ reachable casts that may fail", expFailCasts)

		where:
		scenario                                  | expVPT   | expFPT | expCGE | expPolyCalls | expFailCasts
		"antlr-insensitive-tamiflex.properties"   | 2558501  | 272062 | 58659  | 1993         | 1139
		"antlr-1call-tamiflex.properties"         | 2065468  | 195205 | 56576  | 1914         | 891
		"antlr-1obj+H-tamiflex.properties"        | 1384593  | 108610 | 55054  | 1806         | 847
		"antlr-insensitive-reflection.properties" | 11409446 | 908842 | 64454  | 2083         | 1295
	}

	void equals(String metric, int expectedVal) {
		int actualVal
		analysis.connector.processQuery("_(v) <- Stats:Metrics(\"$metric\", v).")
										{ line -> actualVal = line as int }
		assert actualVal == expectedVal
	}
}
