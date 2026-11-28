package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification

/**
 * Test 118: a single input jar that references a class it does not contain.
 * JPhantom must generate the class (with a single input jar, the phantom classes
 * used to be deleted after JPhantom wrote a complemented ./out.jar).
 */
class TestJPhantomSingleJar extends DoopSpec {

    def "Test 118 (JPhantom with a single input jar)"() {
        when:
        File outJar = new File('out.jar')
        long before = outJar.exists() ? outJar.lastModified() : -1
        Analysis analysis = analyzeTest('118-jphantom-single-jar', 'tests/118-jphantom-single-jar/118-jphantom-single-jar.jar',
                                        ['--platform', 'java_8'] + souffleInterpreter, 'context-insensitive', 'test-118-jphantom-single-jar')

        then:
        rows(analysis, 'Class-Artifact.facts').any { it[0] == 'jphantom-phantoms.jar' && it[1] == 'Gone' }
        rows(analysis, 'Reachable.csv').any { it[0] == '<Gone: int value()>' }
        // nothing written to the working directory
        (outJar.exists() ? outJar.lastModified() : -1) == before
    }

    static List<String[]> rows(Analysis analysis, String file) {
        new File("${analysis.database}/${file}").readLines().collect { it.split('\t') as String[] }
    }
}
