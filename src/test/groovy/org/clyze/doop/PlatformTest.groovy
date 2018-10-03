package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.input.PlatformManager
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test that all platforms can be selected without crashing Doop.
 */
class PlatformTest extends Specification {

    Analysis analysis

    @Unroll
    def "Platform test [#platform]"(String platform) {
        when:
        Main.main((String[])["-i", "http://centauri.di.uoa.gr:8081/artifactory/Demo-benchmarks/test-resources/006-hello-world-1.2.jar",
                             "-a", "context-insensitive",
                             "--id", "dry-run-${platform}",
                             "--platform", platform,
                             "--Xdry-run"])
        analysis = Main.analysis

        then:
        metaExists(analysis)

        where:
        platform << PlatformManager.ARTIFACTS_FOR_PLATFORM.keySet().findAll { !(it.contains("python")) }
    }

}
