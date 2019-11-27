package org.clyze.doop.util

import spock.lang.Specification

class PackageUtilsTest extends Specification {
    def "GetPackageName"(String arg, String res) {
        expect:
        PackageUtils.getPackageName(arg) == res
        where:
        arg     | res
        "a.b.C" | "a.b"
        "C"     | ""
    }
}
