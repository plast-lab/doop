package org.clyze.doop.soot;

import org.clyze.doop.common.JavaRepresentation;
import soot.SootMethod;
import soot.Unit;

import static org.clyze.doop.common.JavaRepresentation.numberedInstructionId;

/**
 * This data structure holds metadata about an instruction/statement.
 */
class InstrInfo {
    /** The index of the instruction in the method body. */
    final int index;
    /** The instruction id. */
    final String insn;
    /** The containing method id. */
    final String methodId;

    InstrInfo(FactWriter factWriter, SootMethod m, Unit unit, Session session, boolean calc) {
        if (calc) {
            this.index = session.calcUnitNumber(unit);
            this.insn = numberedInstructionId(factWriter._rep.signature(m), Representation.getKind(unit), session);
        } else {
            this.index = session.getUnitNumber(unit);
            this.insn = factWriter._rep.instruction(m, unit, index);
        }
        this.methodId = factWriter.methodSig(m, null);
    }

    InstrInfo(String methodId, String kind, Session session) {
        this.index = session.nextNumber(kind);
        this.insn = JavaRepresentation.instructionId(methodId, kind, index);
        this.methodId = methodId;
    }

    @Override
    public String toString() {
        return "InstrInfo{ index=" + index + ", insn=" + insn + ", methodId=" + methodId + " }";
    }
}
