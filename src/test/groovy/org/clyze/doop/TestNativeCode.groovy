package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

class TestNativeCode extends ServerAnalysisTests {
	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 009 (native code)"() {
		when:
		Analysis analysis = analyzeTest("009-native", [ "--simulate-native-returns", "--generate-jimple", "--scan-native-code"])

		then:
		varPointsTo(analysis, '<HelloJNI: void main(java.lang.String[])>/obj#_48', '<native java.lang.Object value allocated in <HelloJNI: java.lang.Object newJNIObj()>>')
		varPointsTo(analysis, '<HelloJNI: void main(java.lang.String[])>/list#_57', '<java.io.UnixFileSystem: java.lang.String[] list(java.io.File)>/new java.lang.String[]/0')
		methodIsReachable(analysis, '<HelloJNI: int helloMethod(java.lang.Object,java.lang.Object)>')
	}
}
