package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import org.clyze.utils.JHelper
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test micro analysis.
 */
class CrudeMicroTest extends DoopSpec {

    // @spock.lang.Ignore
    @Unroll
    def "Crude testing micro/self-contained analyses"() {
        when:
        String id = 'antlr-micro'
        // First run 'micro' analysis.
        List<String> baseArgs = ['-i', Artifacts.ANTLR_JAR, '-a', 'micro', '--id', id, '--dacapo', '--stats', 'none', '--fact-gen-cores', '1', '--platform', 'java_7']
        Main.main((String[])(baseArgs + souffleInterpreter))
        Analysis analysis = Main.analysis
        // Then, run 'self-contained' analysis on the same facts using a different output directory.
        File selfContainedIn  = new File(analysis.outDir, 'database')
        File selfContainedOut = new File(analysis.outDir, 'self-contained-out')
        selfContainedOut.mkdirs()
        JHelper.runWithOutput(['souffle',
                               '--fact-dir', selfContainedIn.canonicalPath,
                               '--output-dir', selfContainedOut.canonicalPath,
                               Doop.souffleLogicPath + '/analyses/micro/self-contained.dl'] as String[], 'SELF-CONTAINED')

        then:
        relationHasApproxSize(analysis, "ArrayIndexPointsTo", 7497)
        relationHasApproxSize(analysis, "Assign", 32658)
        relationHasApproxSize(analysis, "CallGraphEdge", 13873)
        relationHasApproxSize(analysis, "InstanceFieldPointsTo", 539551)
        relationHasApproxSize(analysis, "StaticFieldPointsTo", 783)
        relationHasApproxSize(analysis, "VarPointsTo", 624730)
        assert (new File(selfContainedOut, 'Reachable.csv')).exists()
    }
}
