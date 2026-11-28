package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification

/**
 * Test 122: reflective calls resolved from a TamiFlex log (tests/122-tamiflex).
 * Method.invoke on <Base: void hello()> dispatches on the receiver's run-time type
 * and not on receivers of other classes; the private <Base: void secret()> is
 * called as logged; Plugin, in the library jar and named only in the log, gets
 * facts and its constructor runs.
 */
class TestTamiflexDispatch extends DoopSpec {

    def "Test 122 (TamiFlex: receiver dispatch, private targets, classes named in the log)"() {
        when:
        String dir = 'tests/122-tamiflex'
        Analysis analysis = analyzeTest('122-tamiflex', "${dir}/122-tamiflex.jar",
                                        ['-l', "${dir}/122-tamiflex-lib.jar", '--tamiflex', "${dir}/122-tamiflex.log",
                                         '--platform', 'java_8'] + souffleInterpreter, 'context-insensitive', 'test-122-tamiflex')
        Set<String> reachable = new File("${analysis.database}/Reachable.csv").readLines().collect { it.split('\t')[0] } as Set

        then:
        reachable.contains('<Sub: void hello()>')
        !reachable.contains('<Other: void hello()>')
        reachable.contains('<Base: void secret()>')
        !reachable.contains('<Sub: void secret()>')
        reachable.contains('<Plugin: void <init>()>')
    }
}
