package org.clyze.doop.soot;

import soot.*;
import soot.jimple.*;
import soot.options.Options;

class IrrelevantStmtSwitch implements StmtSwitch
{
    boolean relevant = true;
    Object cause;

    public void caseAssignStmt(AssignStmt stmt)
    {
        Value right = stmt.getRightOp();
        relevant = true;
        if (Options.v().allow_phantom_refs()) {
            // An assignment involving invokedynamic is irrelevant if the
            // bootstrap method is declared in a phantom class.
            if (right instanceof DynamicInvokeExpr)
                inspectBootstrapMethod((DynamicInvokeExpr)right);
            // An assignment instruction is irrelevant if the right-hand
            // side is an invoke expression of a method of a phantom class.
            else if (right instanceof InvokeExpr)
                inspectInvoke((InvokeExpr)right);
        }
    }

    private void inspectBootstrapMethod(DynamicInvokeExpr di) {
        inspectMethod(di.getBootstrapMethodRef().resolve());
    }

    // If a method or its declaring class is phantom, set "relevant" flag.
    private void inspectMethod(SootMethod meth) {
        SootClass methClass = meth.getDeclaringClass();
        if (meth.isPhantom()) {
            relevant = false;
            cause = meth;
        } else if (methClass.isPhantom()) {
            relevant = false;
            cause = methClass;
        }
    }

    public void caseBreakpointStmt(BreakpointStmt stmt) {
        relevant = false;
    }

    public void caseInvokeStmt(InvokeStmt stmt) {
        relevant = true;
        if (Options.v().allow_phantom_refs())
            inspectInvoke(stmt.getInvokeExpr());
    }

    private void inspectInvoke(InvokeExpr expr) {
        if (expr instanceof DynamicInvokeExpr)
            inspectBootstrapMethod((DynamicInvokeExpr)expr);
        else {
            SootMethodRef exprMethodRef = expr.getMethodRef();
            String declClass = exprMethodRef.getDeclaringClass().getName();
            String simpleName = exprMethodRef.getName();
            if (DoopAddons.polymorphicHandling(declClass, simpleName)) {
                relevant = true;
            } else
                inspectMethod(expr.getMethod());
        }
    }

    // According to http://www.brics.dk/SootGuide/sootsurvivorsguide.pdf
    // a Jimple RetStmt is never created when generating Jimple from bytecode.
    public void caseRetStmt(RetStmt stmt) {
        relevant = false;
    }

    public void caseEnterMonitorStmt(EnterMonitorStmt stmt) { relevant = true; }
    public void caseExitMonitorStmt(ExitMonitorStmt stmt)   { relevant = true; }
    public void caseGotoStmt(GotoStmt stmt)                 { relevant = true; }
    public void caseIdentityStmt(IdentityStmt stmt)         { relevant = true; }
    public void caseIfStmt(IfStmt stmt)                     { relevant = true; }
    public void caseNopStmt(NopStmt stmt)                   { relevant = true; }
    public void caseReturnStmt(ReturnStmt stmt)             { relevant = true; }
    public void caseReturnVoidStmt(ReturnVoidStmt stmt)     { relevant = true; }
    public void caseLookupSwitchStmt(LookupSwitchStmt stmt) { relevant = true; }
    public void caseTableSwitchStmt(TableSwitchStmt stmt)   { relevant = true; }
    public void caseThrowStmt(ThrowStmt stmt)               { relevant = true; }

    public void defaultCase(Object obj) {
        throw new RuntimeException("IrrelevantStmtSwitch found non-statement object.");
    }
}
