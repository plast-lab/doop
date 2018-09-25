package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import org.clyze.doop.core.DoopAnalysisFamily
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test that all analyses compile.
 */
class DryRuns extends Specification {

	Analysis analysis
	List analysisNames

	@Unroll
	def "Test analysis compilation"(String analysisName, String stats) {
		when:
		Main.main((String[])["-i", "http://centauri.di.uoa.gr:8081/artifactory/Demo-benchmarks/test-resources/006-hello-world-1.2.jar",
                             "-a", analysisName,
                             "--id", "dry-run-${analysisName}",
                             "--Xdry-run", "--Xforce-recompile",
                             stats])
		analysis = Main.analysis

        then:
        execExists(analysis)

        where:
        analysisName                            | stats
        "1-call-site-sensitive"                 | "--Xstats-full"
        "1-call-site-sensitive+heap"            | "--Xstats-full"
        "1-object-sensitive"                    | "--Xstats-full"
        "1-object-sensitive+heap"               | "--Xstats-full"
        "1-type-sensitive"                      | "--Xstats-full"
        "1-type-sensitive+heap"                 | "--Xstats-full"
        "2-call-site-sensitive+2-heap"          | "--Xstats-full"
        "2-call-site-sensitive+heap"            | "--Xstats-full"
        "2-object-sensitive+2-heap"             | "--Xstats-full"
        "2-object-sensitive+heap"               | "--Xstats-full"
        "2-type-object-sensitive+2-heap"        | "--Xstats-full"
        "2-type-object-sensitive+heap"          | "--Xstats-full"
        "2-type-sensitive+heap"                 | "--Xstats-full"
        "3-object-sensitive+3-heap"             | "--Xstats-full"
        "3-type-sensitive+2-heap"               | "--Xstats-full"
        "3-type-sensitive+3-heap"               | "--Xstats-full"
        "context-insensitive"                   | "--Xstats-full"
        "context-insensitive-plus"              | "--Xstats-full"
        "context-insensitive-plusplus"          | "--Xstats-full"
        "dependency-analysis"                   | "--Xstats-full"
        "micro"                                 | "--Xstats-none"
        "oracular"                              | "--Xstats-full"
        "partitioned-2-object-sensitive+heap"   | "--Xstats-full"
        "scaler"                                | "--Xstats-full"
        "selective-2-object-sensitive+heap"     | "--Xstats-full"
        "sound-may-point-to"                    | "--Xstats-none"
        "twophase-A"                            | "--Xstats-full"
        "twophase-B"                            | "--Xstats-full"
    }
}
