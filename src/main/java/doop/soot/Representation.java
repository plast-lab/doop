package doop.soot;

import soot.*;
import soot.jimple.*;

import java.util.HashMap;
import java.util.Map;

public class Representation
{
  private Map<SootMethod, String> _methodRepr = new HashMap<SootMethod, String>();
  private Map<SootMethod, String> _methodSigRepr = new HashMap<SootMethod, String>();
  private Map<Trap, String> _trapRepr = new HashMap<Trap, String>();

  public String type(SootClass c)
  {
    return c.getName();
  }

  public String type(Type t)
  {
    return t.toString();
  }

  /*
  public String classconstant(Type t)
  {
    return "<class " + type(t) + ">";
  }
   */

  public String classconstant(SootClass c)
  {
    return "<class " + type(c) + ">";
  }

  public String classconstant(Type t)
  {
    return "<class " + type(t) + ">";
  }

  public synchronized String signature(SootMethod m)
  {
    String result = _methodSigRepr.get(m);

    if(result == null)
    {
      result = m.getSignature();
      _methodSigRepr.put(m, result);
    }

    return result;
  }

  public String signature(SootField f)
  {
    return f.getSignature();
  }

  public String simpleName(SootMethod m)
  {
    return m.getName();
  }

  public String simpleName(SootField m)
  {
    return m.getName();
  }

  public String modifier(String m)
  {
    return m;
  }

  public String descriptor(SootMethod m)
  {
    StringBuilder builder = new StringBuilder();

    builder.append(m.getReturnType().toString());
    builder.append("(");
    for(int i = 0; i < m.getParameterCount(); i++)
    {
      builder.append(m.getParameterType(i));

      if(i != m.getParameterCount() - 1)
      {
          builder.append(",");
      }
    }
    builder.append(")");

    return builder.toString();
  }

  public String thisVar(SootMethod m)
  {
    return compactMethod(m) + "/@this";
  }

  public String nativeReturnVar(SootMethod m)
  {
    return compactMethod(m) + "/@native-return";
  }

  public String param(SootMethod m, int i)
  {
    return compactMethod(m) + "/@param" + i;
  }

  public String index(int i)
  {
    return "" + i;
  }

  public String local(SootMethod m, Local l)
  {
    return compactMethod(m) + "/" + l.getName();
  }

  public String newLocalIntermediate(SootMethod m, Local l, Session session)
  {
    String s = local(m, l);
    return s + "/intermediate/" +  session.nextNumber(s);
  }

  public synchronized String handler(SootMethod m, Trap trap, Session session)
  {
    String result = _trapRepr.get(trap);

    if(result == null)
    {
      String name = "catch " + type(trap.getException());
      result = compactMethod(m) + "/" + name + "/" + session.nextNumber(name);

      _trapRepr.put(trap, result);
    }

    return result;
  }

  public String throwLocal(SootMethod m, Local l, Session session)
  {
    String name = "throw " + l.getName();
    return compactMethod(m) + "/" + name + "/" + session.nextNumber(name);
  }

  public String method(SootMethod m)
  {
    return signature(m);
  }

  /**
   * If there is only one method with the name of m, then returns a
   * compact name. Otherwise, the signature is returned.
   */
  public synchronized String compactMethod(SootMethod m)
  {
    String result = _methodRepr.get(m);

    if(result == null) {
      String name = m.getName();

      int count = 0;
      for(SootMethod other : m.getDeclaringClass().getMethods()) {
          if(other.getName().equals(name)) {
              count++;
          }
      }

      if(count > 1) {
          result = m.getSignature();
      }
      else {
          result = m.getDeclaringClass().getName() + "." + name;
      }

      _methodRepr.put(m, result);
    }

    return result;
  }

   private String getKind(Stmt stmt)
  {
    String kind = "unknown";
    if(stmt instanceof AssignStmt)
      kind = "assign";
    else if(stmt instanceof DefinitionStmt)
      kind = "definition";
    else if(stmt instanceof EnterMonitorStmt)
      kind = "enter-monitor";
    else if(stmt instanceof ExitMonitorStmt)
      kind = "exit-monitor";
    else if(stmt instanceof GotoStmt)
      kind = "goto";
    else if(stmt instanceof IdentityStmt)
      kind = "assign";
    else if(stmt instanceof IfStmt)
      kind = "if";
    else if(stmt instanceof InvokeStmt)
      kind = "invoke";
    else if(stmt instanceof RetStmt)
      kind = "ret";
    else if(stmt instanceof ReturnVoidStmt)
      kind = "return-void";
    else if(stmt instanceof ReturnStmt)
      kind = "return";
    else if(stmt instanceof ThrowStmt)
      kind = "throw";
    return kind;
  }

  public String unsupported(SootMethod inMethod, Stmt stmt, Session session, int index)
  {
    return compactMethod(inMethod) +
      "/unsupported " + getKind(stmt) +
      "/" +  stmt.toString() +
      "/instruction" + index;
  }

  /**
   * Text representation of instruction to be used as refmode.
   */
  public String instruction(SootMethod inMethod, Stmt stmt, Session session, int index)
  {
    return compactMethod(inMethod) + "/" + getKind(stmt) + "/instruction" + index;
  }

 public String invoke(SootMethod inMethod, InvokeExpr expr, Session session)
  {
    String name = expr.getMethod().getName();

    return compactMethod(inMethod)
      + "/" + expr.getMethod().getDeclaringClass() + "." + name
      + "/" + session.nextNumber(name);
  }

  public String heapAlloc(SootMethod inMethod, AnyNewExpr expr, Session session)
  {
    if(expr instanceof NewExpr || expr instanceof NewArrayExpr)
    {
      return heapAlloc(inMethod, expr.getType(), session);
    }
    else if(expr instanceof NewMultiArrayExpr)
    {
      return heapAlloc(inMethod, expr.getType(), session);
      //      return compactMethod(inMethod) + "/" + type + "/" +  session.nextNumber(type);
     }
    else
    {
      throw new RuntimeException("Cannot handle new expression: " + expr);
    }
  }

  public String heapMultiArrayAlloc(SootMethod inMethod, NewMultiArrayExpr expr, ArrayType type, Session session)
  {
    return heapAlloc(inMethod, type, session);
  }

  public String heapAlloc(SootMethod inMethod, Type type, Session session)
  {
    String s = type.toString();
    return compactMethod(inMethod) + "/new " + s + "/" +  session.nextNumber(s);
  }

  public String stringconstant(SootMethod inMethod, StringConstant constant)
  {
    String s = constant.toString();
    String content = s.substring(1, s.length() - 1);

    if(content.trim().equals(content) && content.length() > 0)
    {
      return content;
    }
    else
    {
      return "<<\"" + content + "\">>";
    }
  }

  public String numconstant(SootMethod inMethod, NumericConstant constant)
  {
    return constant.toString();
  }
}
