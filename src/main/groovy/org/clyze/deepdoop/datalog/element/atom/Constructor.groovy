package org.clyze.deepdoop.datalog.element.atom

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.VariableExpr

@Canonical
class Constructor extends Functional {

    // The constructed type
    IAtom type

    Constructor(Functional f, IAtom type) {
        super(f.name, f.stage, f.keyExprs, f.valueExpr)
        this.type = type
    }

    def getType() {
        if (type instanceof Stub)
            type = new Entity(type.name, valueExpr)
        return type
    }

    IAtom newAlias(String name, String stage, List<VariableExpr> vars) {
        new Constructor(super.newAlias(name, stage, vars))
    }

    def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
