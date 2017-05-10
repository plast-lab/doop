package org.clyze.deepdoop.datalog

import groovy.transform.Canonical

@Canonical
class Annotation {

    enum Kind { UNDEF, CONSTRUCTOR, ENTITY }

    String name

    def getKind() {
        if (name == "Constructor") return Kind.CONSTRUCTOR
        if (name == "Entity") return Kind.ENTITY
        return Kind.UNDEF
    }
}
