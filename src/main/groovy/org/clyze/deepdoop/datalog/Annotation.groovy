package org.clyze.deepdoop.datalog

import groovy.transform.Canonical

@Canonical
class Annotation {

    enum Kind { UNDEF, CONSTRUCTOR, ENTITY }

    String name

    def getKind() {
        name = name.toLowerCase()
        if (name == "constructor") return Kind.CONSTRUCTOR
        if (name == "entity") return Kind.ENTITY
        return Kind.UNDEF
    }
}
