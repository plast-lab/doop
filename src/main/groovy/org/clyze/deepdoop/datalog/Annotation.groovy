package org.clyze.deepdoop.datalog

import groovy.transform.Canonical

@Canonical
class Annotation {

    enum Kind { UNDEF, CONSTRUCTOR }

    String name

    def getKind() {
        if (name == "Constructor") return Kind.CONSTRUCTOR
        else return Kind.UNDEF
    }
}
