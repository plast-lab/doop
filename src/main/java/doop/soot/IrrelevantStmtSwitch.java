package doop.soot;

import soot.jimple.*;
import soot.jimple.StmtSwitch;
import soot.Value;

public class IrrelevantStmtSwitch implements StmtSwitch
{
    public boolean relevant = true;

    public void caseAssignStmt(AssignStmt stmt)
    {
        Value right = stmt.getRightOp();

        // An assignment instruction is irrelevant if the right
        // hand side is an invoke expression of a method of a
        // phantom class
        relevant = !(soot.options.Options.v().allow_phantom_refs()
                     && (right instanceof InvokeExpr)
                     && ((InvokeExpr) right).getMethodRef()
                                            .declaringClass()
                                            .isPhantom());
    }

    public void caseBreakpointStmt(BreakpointStmt stmt)
    {
        relevant = false;
    }

    public void caseEnterMonitorStmt(EnterMonitorStmt stmt)
    {
        relevant = true;
    }

    public void caseExitMonitorStmt(ExitMonitorStmt stmt)
    {
        relevant = true;
    }

    public void caseGotoStmt(GotoStmt stmt)
    {
        relevant = true;
    }

    public void caseIdentityStmt(IdentityStmt stmt)
    {
        relevant = true;
    }

    public void caseIfStmt(IfStmt stmt)
    {
        relevant = true;
    }

    public void caseInvokeStmt(InvokeStmt stmt)
    {
        if (soot.options.Options.v().allow_phantom_refs())
            relevant = !stmt.getInvokeExpr().getMethodRef().declaringClass().isPhantom();
        else
            relevant = true;
    }

    public void caseLookupSwitchStmt(LookupSwitchStmt stmt)
    {
        relevant = true;
    }

    public void caseNopStmt(NopStmt stmt)
    {
        relevant = true;
    }

    // According to http://www.brics.dk/SootGuide/sootsurvivorsguide.pdf
    // a Jimple RetStmt is never created when generating Jimple from bytecode.
    public void caseRetStmt(RetStmt stmt)
    {
        relevant = false;
    }

    public void caseReturnStmt(ReturnStmt stmt)
    {
        relevant = true;
    }

    public void caseReturnVoidStmt(ReturnVoidStmt stmt)
    {
        relevant = true;
    }

    public void caseTableSwitchStmt(TableSwitchStmt stmt)
    {
        relevant = true;
    }

    public void caseThrowStmt(ThrowStmt stmt)
    {
        relevant = true;
    }

    public void defaultCase(Object obj)
    {
        throw new RuntimeException("uh, why is this invoked?");
    }
}
