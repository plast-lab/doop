package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Unroll

class TestSoundMayPointsTo extends DoopSpec {
    @Unroll
    def "Sound-may-point-to analysis test"() {
        when:
        List args = ['-i', Artifacts.HELLO_JAR,
                     '-a', 'sound-may-point-to',
                     '--id', 'sound-may-point-to-test',
                     '-Ldebug']
        Main.main((String[])args)
        Analysis analysis = Main.analysis

        then:
        analysis != null
    }
}
