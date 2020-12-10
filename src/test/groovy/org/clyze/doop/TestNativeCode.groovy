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
		List args = ["--simulate-native-returns",
					 "--generate-jimple",
					 "--scan-native-code"] + testExports
		Analysis analysis = analyzeTest("009-native", args)

		then:
		methodIsReachable(analysis, '<HelloJNI: int helloMethod(java.lang.Object,java.lang.Object)>')
		varPointsToQ(analysis, '<HelloJNI: void main(java.lang.String[])>/obj#_52', '<native java.lang.Object value allocated in <HelloJNI: java.lang.Object newJNIObj()>>')
		varPointsToQ(analysis, '<HelloJNI: void main(java.lang.String[])>/list#_61', '<java.io.UnixFileSystem: java.lang.String[] list(java.io.File)>/new java.lang.String[]/0')
	}
}
