package org.clyze.doop.soot

import spock.lang.Specification

class DoopConventionsTest extends Specification {
    def "JimpleDir"(String arg, String res) {
        expect:
        DoopConventions.jimpleDir(arg) == res
        where:
        arg | res
        "x" | "x/jimple"
    }

    /**
     * Invoking setSeparator() should not fail if Soot is available.
     */
    def "SetSeparator"() {
        when:
        DoopConventions.setSeparator()
        then:
        DoopConventions.setSeparatorFailed == false
    }
}
