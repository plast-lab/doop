package org.clyze.deepdoop.datalog

import groovy.transform.Canonical

@Canonical
class Annotation {

    enum Kind {
        CONSTRUCTOR,
        ENTITY,
        INPUT,
        OUTPUT,
        UNDEF
    }

    String name

    def getKind() {
        name = name.toLowerCase()
        switch (name) {
            case "constructor": return Kind.CONSTRUCTOR
            case "entity": return Kind.ENTITY
            case "input": return Kind.INPUT
            case "output": return Kind.OUTPUT
            default: return Kind.UNDEF
        }
    }
}
