package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification

/**
 * Test 119: generating the facts of the same input twice gives identical facts
 * (on master, two runs on antlr differed in 44 of their fact files) and
 * identical IR (`--generate-jimple`, Shimple by default: Phi nodes and the
 * methods Soot adds to MethodHandle for signature-polymorphic calls were
 * ordered by thread timing and identity hashes).
 */
class TestDeterministicFacts extends DoopSpec {

    def "Test 119 (two fact generation runs give identical facts)"() {
        when:
        List<String> args = ['--platform', 'java_8', '--facts-only', '--generate-jimple']
        String antlr = 'https://github.com/plast-lab/doop-benchmarks/raw/refs/heads/main/dacapo-2006/antlr.jar'
        File db1 = analyzeTest('119-deterministic-facts', antlr, args, 'context-insensitive', 'test-119-deterministic-facts-1').database
        File db2 = analyzeTest('119-deterministic-facts', antlr, args, 'context-insensitive', 'test-119-deterministic-facts-2').database
        List<String> facts = db1.list().findAll { it.endsWith('.facts') }.sort()

        then:
        facts.size() > 100
        facts.findAll { lines(new File(db1, it)) != lines(new File(db2, it)) } == []
        irFiles(db1).size() > 1000
        irFiles(db1) == irFiles(db2)
        irFiles(db1).findAll { !Arrays.equals(new File(db1, it).bytes, new File(db2, it).bytes) } == []
    }

    /** Paths of the IR files, relative to the database directory. */
    static List<String> irFiles(File db) {
        File ir = new File(db, 'jimple')
        List<String> files = []
        ir.eachFileRecurse(groovy.io.FileType.FILES) { files << db.toPath().relativize(it.toPath()).toString() }
        files.sort()
    }

    /**
     * The file's lines as a multiset, in constant memory: the line count and
     * the sums (mod 2^64) of two independent 64-bit hashes of each line. The
     * fact files are compared as sets of rows, not in write order; reading
     * them whole exhausted the test JVM's heap.
     */
    static List<Long> lines(File f) {
        long n = 0, h1 = 0, h2 = 0
        java.security.MessageDigest md = java.security.MessageDigest.getInstance('SHA-256')
        f.eachLine('UTF-8') { String line ->
            byte[] d = md.digest(line.getBytes('UTF-8'))
            java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(d)
            n++
            h1 += b.getLong(0)
            h2 += b.getLong(8)
        }
        [n, h1, h2]
    }
}
