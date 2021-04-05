package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

class TestLightReflection extends DoopSpec {
    // @spock.lang.Ignore
    @Unroll
    def "Server analysis test 016 (light reflection glue)"() {
        when:
        List options = ["--light-reflection-glue",
                        "--thorough-fact-gen",
                        "--platform", "java_8"] + sanityOpts
        Analysis analysis = analyzeBuiltinTest("016-reflection",
                                               options,
                                               'context-insensitive',
                                               'test-016-reflection-glue')

        then:
        varPointsToQ(analysis, '<ReflectionTest: void main(java.lang.String[])>/cA#_31', '<class A>')
        noSanityErrors(analysis)
    }
}
