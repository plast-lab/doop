package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll

class CrudeSpec extends Specification {

	Analysis analysis

	@Unroll
	def "Crude testing based on sample metrics similarity"() {
		when:
		def propertyFile = this.class.getResource("/scenarios/$scenario").file
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
		"antlr-insensitive-tamiflex.properties"   | 2595458  | 271819  | 57869  | 1957         | 1123
		"antlr-1call-tamiflex.properties"         | 10458878 | 192361  | 55859  | 1887         | 954
		"antlr-1objH-tamiflex.properties"         | 6695712  | 104251  | 54545  | 1811         | 926
		"antlr-insensitive-reflection.properties" | 8808591  | 1111250 | 77719  | 2499         | 1619
	}

	void equals(String metric, long expectedVal) {
		long actualVal = -1
		def cmd = [analysis.options.BLOXBATCH.value as String, '-db', analysis.database as String, '-query', "_(v) <- Stats:Metrics(_, \"$metric\", v)."]
		analysis.executor.execute(cmd) { line -> actualVal = line as long }
		assert actualVal == expectedVal
	}
}
