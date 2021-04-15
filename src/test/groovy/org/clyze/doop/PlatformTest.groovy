package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.input.PlatformManager
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test that all platforms can be selected without crashing Doop.
 */
class PlatformTest extends Specification {

    // @spock.lang.Ignore
    @Unroll
    def "Platform test [#platform]"(String platform) {
        when:
        Main.main((String[])["-i", Artifacts.HELLO_JAR,
                             "-a", "context-insensitive",
                             "--id", "dry-run-${platform}",
                             "--platform", platform,
                             "--facts-only"])
        Analysis analysis = Main.analysis

        then:
        metaExists(analysis)

        where:
        platform << PlatformManager.ARTIFACTS_FOR_PLATFORM.keySet().findAll { !(it.contains("python")) }
    }

}
