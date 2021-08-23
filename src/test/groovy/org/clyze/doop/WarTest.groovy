package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Unroll

class WarTest extends DoopSpec {
    // @spock.lang.Ignore
    @Unroll
    def "Test WAR support"() {
        when:
        // Some profiles may not support full stats or server logic.
        Main.main((String[])(['-i', Artifacts.DAYTRADER_WAR,
                              '-a', 'context-insensitive', '-Ldebug',
                              '--souffle-mode', 'interpreted',
                              '--open-programs', 'jackee',
                              '--id', 'daytrader']))
        Analysis analysis = Main.analysis

        then:
        assert true
   }
}