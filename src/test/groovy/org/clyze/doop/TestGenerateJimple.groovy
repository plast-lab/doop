package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Test 127: --generate-jimple writes the bodies the facts were generated from
 * (Shimple, locals renamed as in the facts), also with --Xlow-mem: the locals
 * each method of Main declares in Main.shimple are exactly the locals its facts name.
 */
class TestGenerateJimple extends DoopSpec {

    @Unroll
    def "Test 127 (Jimple/Shimple output matches the facts #mode)"() {
        when:
        Analysis analysis = analyzeTest('120-indy-arg-order', 'tests/120-indy-arg-order/120-indy-arg-order.jar',
                                        ['--platform', 'java_8', '--facts-only', '--generate-jimple'] + mode,
                                        'context-insensitive', "test-127-generate-jimple${mode ? '-low-mem' : ''}")
        Map<String, Set<String>> jimple = jimpleLocals(new File("${analysis.database}/jimple/Main.shimple"))
        Map<String, Set<String>> facts = factLocals(analysis, 'Main')

        then:
        !facts.isEmpty()
        jimple == facts

        where:
        mode << [[], ['--Xlow-mem']]
    }

    /** Method (Jimple header, e.g. "void put(java.lang.Object)") -> declared locals. */
    static Map<String, Set<String>> jimpleLocals(File f) {
        Map<String, Set<String>> locals = [:]
        String method = null
        for (String line : f.readLines().collect { it.replace("'", '') }) {
            def header = line =~ /^ {4}(?:[a-z]+ )*(\S+) (\S+)\((.*)\)(?: throws .*)?$/
            if (header.matches()) {
                method = "${header.group(1)} ${header.group(2)}(${header.group(3).replace(', ', ',')})"
                locals[method] = [] as Set
            } else if (line == '    }') {
                method = null
            } else if (method) {
                def decl = line.trim() =~ /^(\S+) ([^=();:]+);$/
                if (decl.matches() && !(decl.group(1) in ['return', 'goto', 'throw', 'entermonitor', 'exitmonitor', 'nop', 'catch', 'if']))
                    decl.group(2).split(',').collect { it.trim() }.findAll { !it.contains(' ') }.each { locals[method] << it }
            }
        }
        return locals.findAll { !it.value.isEmpty() }
    }

    /** Methods of the class with a body in the facts -> the locals the facts name. */
    static Map<String, Set<String>> factLocals(Analysis analysis, String cls) {
        Map<String, String> name = new File("${analysis.database}/Var-SimpleName.facts").readLines()
            .collect { it.split('\t') }.collectEntries { [(it[0]): it[1]] }
        Map<String, Set<String>> locals = [:]
        new File("${analysis.database}/Var-DeclaringMethod.facts").readLines().collect { it.split('\t') }.each {
            String var = it[0], m = it[1]
            if (!m.startsWith("<${cls}: ")) return
            String n = name[var] ?: var.substring(m.length() + 1)
            // formals/this and the fact writer's synthetic constant variables are not Jimple locals
            if (n.startsWith('@') || n.startsWith('<') || n ==~ /\$(numconstant|null|stringconstant|classconstant|methodtypeconstant|methodhandleconstant|mhandleconstant)\d*/ || n.contains('/intermediate/'))
                return
            String sig = m.substring(m.indexOf(': ') + 2, m.length() - 1)
            locals.computeIfAbsent(sig) { [] as Set } << n
        }
        return locals
    }
}
