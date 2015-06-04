package doop.soot;

import soot.Local;
import soot.RefLikeType;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Trap;

import soot.Type;
import soot.PrimType;
import soot.RefType;
import soot.NullType;
import soot.ArrayType;
import soot.VoidType;

import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocal;
import soot.tagkit.LineNumberTag;

import static doop.PredicateFile.*;

/**
 * FactWriter determines the format of a fact and adds it to a
 * database. No traversal code here (see FactGenerator for that).
 *
 * @author Martin Bravenboer
 * @license MIT
 */
public class FactWriter
{

    private Database _db;
    private Representation _rep;

    public FactWriter(Database db)
    {
        _db = db;
        _rep = new Representation();
    }

    public void writeStandardFacts()
    {
        // _db.addEntity(PRIMITIVE_TYPE, "boolean");
        // _db.addEntity(PRIMITIVE_TYPE, "byte");
        // _db.addEntity(PRIMITIVE_TYPE, "char");
        // _db.addEntity(PRIMITIVE_TYPE, "short");
        // _db.addEntity(PRIMITIVE_TYPE, "int");
        // _db.addEntity(PRIMITIVE_TYPE, "long");
        // _db.addEntity(PRIMITIVE_TYPE, "float");
        // _db.addEntity(PRIMITIVE_TYPE, "double");
        // _db.addEntity(NULL_TYPE, "null_type");
    }


    public void writeProperty(String path, String key, String value)
    {
        // Add heap allocations for string constants
        _db.add(STRING_CONST, _db.asEntity(path), _db.asEntity("java.lang.String"));
        _db.add(STRING_CONST, _db.asEntity(key), _db.asEntity("java.lang.String"));
        _db.add(STRING_CONST, _db.asEntity(value), _db.asEntity("java.lang.String"));

        _db.add(PROPERTIES,
                _db.asEntity(path),
                _db.asEntity(key),
                _db.asEntity(value));
    }

    public void writeClassOrInterfaceType(SootClass c)
    {
        String rep = _rep.type(c);
        Column col;

        if(c.isInterface())
        {
            col = _db.addEntity(INTERFACE_TYPE, rep);
        }
        else
        {
            col = _db.addEntity(CLASS_TYPE, rep);
        }

        _db.add(CLASS_OBJ,
                _db.asEntity(_rep.classconstant(c)),
                _db.addEntity(CLASS_TYPE, "java.lang.Class"),
                col);
    }

    public void writeDirectSuperclass(SootClass sub, SootClass sup)
    {
        _db.add(DIRECT_SUPER_CLASS,
                writeType(sub),
                writeType(sup));
    }

    public void writeDirectSuperinterface(SootClass clazz, SootClass iface)
    {
        _db.add(DIRECT_SUPER_IFACE,
                writeType(clazz),
                writeType(iface));
    }

    public Column writeType(SootClass c)
    {
        String result = _rep.type(c);

        // The type itself is already taken care of by writing the
        // SootClass declaration, so we don't actually write the type
        // here, and just return the string.

        return _db.asEntity(result);
    }

    public Column writeType(Type t)
    {
        String result = _rep.type(t);
        Column c;

        if(t instanceof ArrayType)
        {
            c = _db.addEntity(ARRAY_TYPE, result);
            Type componentType = ((ArrayType) t).getElementType();
            _db.add(COMPONENT_TYPE, c, writeType(componentType));
        }
        else if(t instanceof PrimType || t instanceof NullType)
        {
            // taken care of by the standard facts
            c = _db.asEntity(result);
        }
        else if(t instanceof RefType)
        {
            // taken care of by SootClass declaration.
            c = _db.asEntity(result);
        }
        else
        {
            throw new RuntimeException("Don't know what to do with type " + t);
        }

        return c;
    }

    public void writeEnterMonitor(SootMethod m, Stmt stmt, Local var, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(ENTER_MONITOR,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.addEntity(VAR, _rep.local(m, var)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeExitMonitor(SootMethod m, Stmt stmt, Local var, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(EXIT_MONITOR,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.addEntity(VAR, _rep.local(m, var)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

   public void writeAssignLocal(SootMethod m, Stmt stmt, Local to, Local from, Session session)
   {
       int index = session.calcUnitNumber(stmt);
       String rep = _rep.instruction(m, stmt, session, index);

       _db.add(ASSIGN_LOCAL,
               _db.asEntity(rep),
               _db.asIntColumn(String.valueOf(index)),
               _db.addEntity(VAR, _rep.local(m, from)),
               _db.addEntity(VAR, _rep.local(m, to)),
               _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeAssignLocal(SootMethod m, Stmt stmt, Local to, ThisRef ref, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(ASSIGN_LOCAL,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.addEntity(VAR, _rep.thisVar(m)),
                _db.addEntity(VAR, _rep.local(m, to)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeAssignLocal(SootMethod m, Stmt stmt, Local to, ParameterRef ref, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(ASSIGN_LOCAL,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.addEntity(VAR, _rep.param(m, ref.getIndex())),
                _db.addEntity(VAR, _rep.local(m, to)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeAssignInvoke(SootMethod inMethod, Stmt stmt, Local to, InvokeExpr expr, Session session)
    {
        String rep = writeInvokeHelper(inMethod, stmt, expr, session);

        _db.add(ASSIGN_RETURN_VALUE,
                _db.asEntity(rep),
                _db.addEntity(VAR, _rep.local(inMethod, to)));
    }

    public void writeAssignHeapAllocation(SootMethod m, Stmt stmt, Local l, AnyNewExpr expr, Session session)
    {
        String heap = _rep.heapAlloc(m, expr, session);

        _db.add(NORMAL_OBJ,
                _db.asEntity(heap),
                writeType(expr.getType()));

        if(expr instanceof NewArrayExpr)
        {
            NewArrayExpr newArray = (NewArrayExpr) expr;
            Value sizeVal = newArray.getSize();

            if(sizeVal instanceof IntConstant)
            {
                IntConstant size = (IntConstant) sizeVal;

                if(size.value == 0)
                    _db.add(EMPTY_ARRAY,
                            _db.asEntity(heap));
            }
        }

        // statement
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(ASSIGN_HEAP_ALLOC,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.asEntity(heap),
                _db.addEntity(VAR, _rep.local(m, l)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));

    }

    private Type getComponentType(ArrayType type)
    {
        // Soot calls the component type of an array type the "element
        // type", which is rather confusing, since in an array type
        // A[][][], the JVM Spec defines A to be the element type, and
        // A[][] is the component type.
        return type.getElementType();
    }

    /**
     * NewMultiArray is slightly complicated because an array needs to
     * be allocated separately for every dimension of the array.
     */
   public void writeAssignNewMultiArrayExpr(SootMethod m, Stmt stmt, Local l, NewMultiArrayExpr expr, Session session)
    {
        writeAssignNewMultiArrayExprHelper(m, stmt, l, _rep.local(m,l), expr, (ArrayType) expr.getType(), session);
    }

    private void writeAssignNewMultiArrayExprHelper(SootMethod m, Stmt stmt, Local l, String assignTo, NewMultiArrayExpr expr, ArrayType arrayType, Session session) 
    {
        String heap = _rep.heapMultiArrayAlloc(m, expr, arrayType, session);
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(NORMAL_OBJ,
                _db.asEntity(heap),
                writeType(arrayType));

        _db.add(ASSIGN_HEAP_ALLOC,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.asEntity(heap),
                _db.addEntity(VAR, assignTo),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
        
        Type componentType = getComponentType(arrayType);
        if (componentType instanceof ArrayType) {
            String childAssignTo = _rep.newLocalIntermediate(m, l, session);
            writeAssignNewMultiArrayExprHelper(m, stmt, l, childAssignTo, expr, (ArrayType) componentType, session);
            int storeInsnIndex = session.calcUnitNumber(stmt);
            String storeInstrRep = _rep.instruction(m, stmt, session, storeInsnIndex);

            _db.add(STORE_ARRAY_INDEX,
                    _db.asEntity(storeInstrRep),
                    _db.asIntColumn(String.valueOf(storeInsnIndex)),
                    _db.addEntity(VAR, childAssignTo),
                    _db.addEntity(VAR, assignTo),
                    _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));

            _db.add(VAR_TYPE,
                    _db.addEntity(VAR, childAssignTo),
                    writeType(componentType));

            _db.add(VAR_DECLARING_METHOD,
                    _db.asEntity(childAssignTo),
                    _db.asEntity(_rep.method(m)));

        }
    }


    // The commented-out code below is what used to be in Doop2. It is not
    // equivalent to code in old Doop. I (YS) tried to have a more compatible
    // approach for comparison purposes.
    /*
    public void writeAssignNewMultiArrayExpr(SootMethod m, Stmt stmt, Local l, NewMultiArrayExpr expr, Session session)
    {
        // what is a normal object?
        String heap = _rep.heapAlloc(m, expr, session);

        _db.add("NormalObject",
                _db.asEntity(heap),
                writeType(expr.getType()));

        // local variable to assign the current array allocation to.
        String assignTo = _rep.local(m, l);

        Type type = (ArrayType) expr.getType();
        int dimensions = 0;
        while(type instanceof ArrayType)
            {
                ArrayType arrayType = (ArrayType) type;

                // make sure we store the type
                writeType(type);

                type = getComponentType(arrayType);
                dimensions++;
            }

        Type elementType = type;

        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add("AssignMultiArrayAllocation",
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.asEntity(heap),
                _db.asIntColumn(String.valueOf(dimensions)),
                _db.addEntity(VAR, assignTo),
                _db.asEntity("MethodSignature", _rep.method(m)));

    // idea: do generate the heap allocations, but not the assignments
    // (to array indices). Do store the type of those heap allocations
    }
    */

    public void writeAssignStringConstant(SootMethod m, Stmt stmt, Local l, StringConstant s, Session session)
    {
        String heap = _rep.stringconstant(m, s);

        // statement
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        // write heap allocation
        _db.add(STRING_CONST, _db.asEntity(heap), writeType(s.getType()));

        _db.add(ASSIGN_HEAP_ALLOC,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.asEntity(heap),
                _db.addEntity(VAR, _rep.local(m, l)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeAssignNull(SootMethod m, Stmt stmt, Local l,  Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(ASSIGN_NULL,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.addEntity(VAR, _rep.local(m, l)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeAssignNumConstant(SootMethod m, Stmt stmt, Local l, NumericConstant constant, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(ASSIGN_NUM_CONST,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.asEntity(_rep.numconstant(m, constant)),
                _db.addEntity(VAR, _rep.local(m, l)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeAssignClassConstant(SootMethod m, Stmt stmt, Local l, ClassConstant constant, Session session)
    {
        // System.err.println("class constant " + constant + " in " + m);
        String s = constant.getValue().replace('/', '.');

        String heap;
        String actualType;

        /* There is some weirdness in class constants: normal Java class
           types seem to have been translated to a syntax with the initial
           L, but arrays are still represented as [, for example [C for
           char[] */
        if(s.charAt(0) == '[')
        {
            // array type
            Type t = soot.coffi.Util.v().jimpleTypeOfFieldDescriptor(s);

            heap = _rep.classconstant(t);
            actualType = _rep.type(t);
            //_db.add("ReifiedClass", _db.asEntity(_rep.type(t)), _db.asEntity(heap));

            // TODO only classes have their heap allocation type written already
        }
        else
        {
            SootClass c = soot.Scene.v().getSootClass(s);
            if(c == null)
            {
                throw new RuntimeException("Unexpected class constant: " + constant);
            }

            heap =  _rep.classconstant(c);
            actualType = _rep.type(c);
        }

        // write heap allocation
        _db.add(CLASS_OBJ,
                _db.asEntity(heap),
                _db.addEntity(CLASS_TYPE, "java.lang.Class"),
                _db.asEntity(actualType));

        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(ASSIGN_HEAP_ALLOC,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                // REVIEW: the class object is not explicitly written. Is this always ok?
                _db.asEntity(heap),
                _db.addEntity(VAR, _rep.local(m, l)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeAssignCast(SootMethod m, Stmt stmt, Local to, Local from, Type t, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(ASSIGN_CAST,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.addEntity(VAR, _rep.local(m, from)),
                _db.addEntity(VAR, _rep.local(m, to)),
                writeType(t),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeStoreInstanceField(SootMethod m, Stmt stmt, SootField f, Local base, Local from, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(STORE_INST_FIELD,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.addEntity(VAR, _rep.local(m, from)),
                _db.addEntity(VAR, _rep.local(m, base)),
                _db.asEntity(FIELD_SIGNATURE, _rep.signature(f)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeLoadInstanceField(SootMethod m, Stmt stmt, SootField f, Local base, Local to, Session session)
    {
        int index = session.calcUnitNumber(stmt);

        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(LOAD_INST_FIELD,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.addEntity(VAR, _rep.local(m, to)),
                _db.addEntity(VAR, _rep.local(m, base)),
                _db.asEntity(FIELD_SIGNATURE, _rep.signature(f)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeStoreStaticField(SootMethod m, Stmt stmt, SootField f, Local from, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(STORE_STATIC_FIELD,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.addEntity(VAR, _rep.local(m, from)),
                _db.asEntity(FIELD_SIGNATURE, _rep.signature(f)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeLoadStaticField(SootMethod m, Stmt stmt, SootField f, Local to, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(LOAD_STATIC_FIELD,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.addEntity(VAR, _rep.local(m, to)),
                _db.asEntity(FIELD_SIGNATURE, _rep.signature(f)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeLoadArrayIndex(SootMethod m, Stmt stmt, Local base, Local to, Session session)
    {
        int index = session.calcUnitNumber(stmt);

        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(LOAD_ARRAY_INDEX,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.addEntity(VAR, _rep.local(m, to)),
                _db.addEntity(VAR, _rep.local(m, base)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeStoreArrayIndex(SootMethod m, Stmt stmt, Local base, Local from, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(STORE_ARRAY_INDEX,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(index)),
                _db.addEntity(VAR, _rep.local(m, from)),
                _db.addEntity(VAR, _rep.local(m, base)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeApplicationClass(SootClass application)
    {
        _db.add(APP_CLASS,
                writeType(application));
    }

    public void writeFieldSignature(SootField f)
    {
        _db.add(FIELD_SIGNATURE,
                _db.asEntity(_rep.signature(f)),
                writeType(f.getDeclaringClass()),
                _db.addEntity(SIMPLE_NAME, _rep.simpleName(f)),
                writeType(f.getType()));
    }

    public void writeFieldModifier(SootField f, String modifier)
    {
        _db.add(FIELD_MODIFIER,
                _db.addEntity(MODIFIER, _rep.modifier(modifier)),
                _db.asEntity(FIELD_SIGNATURE, _rep.signature(f)));
    }

    public void writeMethodSignature(SootMethod m)
    {
        _db.add(METHOD_SIGNATURE,
                _db.asEntity(_rep.signature(m)),
                _db.asEntity(_rep.simpleName(m)),
                _db.asEntity(_rep.descriptor(m)),
                writeType(m.getDeclaringClass()));
    }

    public void writeMethodModifier(SootMethod m, String modifier)
    {
        _db.add(METHOD_MODIFIER,
                _db.addEntity(MODIFIER, _rep.modifier(modifier)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeReturn(SootMethod m, Stmt stmt, Local l, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(RETURN,
                _db.asEntity(rep),
                // index
                _db.asIntColumn(String.valueOf(index)),
                // var
                _db.addEntity(VAR, _rep.local(m, l)),
                // method
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeReturnVoid(SootMethod m, Stmt stmt, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(RETURN_VOID,
                _db.asEntity(rep),
                // index
                _db.asIntColumn(String.valueOf(index)),
                // method
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    // The return var of native methods is exceptional, in that it does not
    // correspond to a return instruction.
    public void writeNativeReturnVar(SootMethod m)
    {
        if(!(m.getReturnType() instanceof VoidType))
        {
            _db.add(NATIVE_RETURN_VAR,
                    _db.addEntity(VAR, _rep.nativeReturnVar(m)),
                    _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));

            _db.add(VAR_TYPE,
                    _db.asEntity(_rep.nativeReturnVar(m)),
                    writeType(m.getReturnType()));

            _db.add(VAR_DECLARING_METHOD,
                    _db.asEntity(_rep.nativeReturnVar(m)),
                    _db.asEntity(_rep.method(m)));
        }
    }

    public void writeGoto(SootMethod m, Stmt stmt, Unit to, Session session)
    {
        // index was already computed earlier
        int index = session.getUnitNumber(stmt);

        // index was already computed earlier
        int indexTo = session.getUnitNumber(to);

        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(GOTO,
                _db.asEntity(rep),
                // index
                _db.asIntColumn(String.valueOf(index)),
                // to
                _db.asIntColumn(String.valueOf(indexTo)),
                // method
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    /**
     * If
     */
    public void writeIf(SootMethod m, Stmt stmt, Unit to, Session session)
    {
        // index was already computed earlier
        int index = session.getUnitNumber(stmt);

        // index was already computed earlier
        int indexTo = session.getUnitNumber(to);

        String rep = _rep.instruction(m, stmt, session, index);

        _db.add(IF,
                _db.asEntity(rep),
                // index
                _db.asIntColumn(String.valueOf(index)),
                // to
                _db.asIntColumn(String.valueOf(indexTo)),
                // method
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeTableSwitch(SootMethod inMethod, TableSwitchStmt stmt, Session session)
    {
        int stmtIndex = session.getUnitNumber(stmt);

        Value v = writeImmediate(inMethod, stmt, stmt.getKey(), session);

        if(!(v instanceof Local))
            throw new RuntimeException("Unexpected key for TableSwitch statement " + v + " " + v.getClass());

        Local l = (Local) v;

        String rep = _rep.instruction(inMethod, stmt, session, stmtIndex);

        _db.add(TABLE_SWITCH,
                _db.asEntity(rep),
                // index
                _db.asIntColumn(String.valueOf(stmtIndex)),
                // key
                _db.addEntity(VAR, _rep.local(inMethod, l)),
                // method
                _db.asEntity(METHOD_SIGNATURE, _rep.method(inMethod)));

        for(int tgIndex = stmt.getLowIndex(), i = 0; tgIndex <= stmt.getHighIndex(); tgIndex++, i++)
        {
            int indexTo = session.getUnitNumber(stmt.getTarget(i));

            _db.add(TABLE_SWITCH_TARGET,
                    _db.asEntity(rep),
                    // index
                    _db.asIntColumn(String.valueOf(tgIndex)),
                    // target
                    _db.asIntColumn(String.valueOf(indexTo)));
        }

        int defaultIndex = session.getUnitNumber(stmt.getDefaultTarget());

        _db.add(TABLE_SWITCH_DEFAULT,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(defaultIndex)));
    }

    public void writeLookupSwitch(SootMethod inMethod, LookupSwitchStmt stmt, Session session)
    {
        int stmtIndex = session.getUnitNumber(stmt);

        Value v = writeImmediate(inMethod, stmt, stmt.getKey(), session);

        if(!(v instanceof Local))
            throw new RuntimeException("Unexpected key for TableSwitch statement " + v + " " + v.getClass());

        Local l = (Local) v;

        String rep = _rep.instruction(inMethod, stmt, session, stmtIndex);

        _db.add(LOOKUP_SWITCH,
                _db.asEntity(rep),
                // index
                _db.asIntColumn(String.valueOf(stmtIndex)),
                // key
                _db.addEntity(VAR, _rep.local(inMethod, l)),
                // method
                _db.asEntity(METHOD_SIGNATURE, _rep.method(inMethod)));;

        for(int i = 0, end = stmt.getTargetCount(); i < end; i++)
        {
            int tgIndex = stmt.getLookupValue(i);
            int indexTo = session.getUnitNumber(stmt.getTarget(i));

            _db.add(LOOKUP_SWITCH_TARGET,
                    _db.asEntity(rep),
                    // index
                    _db.asIntColumn(String.valueOf(tgIndex)),
                    // target
                    _db.asIntColumn(String.valueOf(indexTo)));
        }

        int defaultIndex = session.getUnitNumber(stmt.getDefaultTarget());

        _db.add(LOOKUP_SWITCH_DEFAULT,
                _db.asEntity(rep),
                _db.asIntColumn(String.valueOf(defaultIndex)));
    }

    public void writeUnsupported(SootMethod m, Stmt stmt, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.unsupported(m, stmt, session, index);

        _db.add(UNSUPPORTED_INSTRUCTION,
                _db.asEntity(rep),
                // index
                _db.asIntColumn(String.valueOf(index)),
                // method
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    /**
     * Throw statement
     */
    public void writeThrow(SootMethod m, Stmt stmt, Local l, Session session)
    {
        int index = session.calcUnitNumber(stmt);
        String rep = _rep.throwLocal(m, l, session);

        _db.add(THROW,
                _db.asEntity(rep),
                // index
                _db.asIntColumn(String.valueOf(index)),
                // var
                _db.addEntity(VAR, _rep.local(m, l)),
                // method
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeExceptionHandlerPrevious(SootMethod m, Trap current, Trap previous, Session session)
    {
        _db.add(EXCEPT_HANDLER_PREV,
                _db.asEntity(_rep.handler(m, current, session)),
                _db.asEntity(_rep.handler(m, previous, session)));
    }

    public void writeExceptionHandler(SootMethod m, Trap handler, Session session)
    {
        SootClass exc = handler.getException();

        Local caught;
        {
            Unit handlerUnit = handler.getHandlerUnit();
            IdentityStmt stmt = (IdentityStmt) handlerUnit;
            Value left = stmt.getLeftOp();
            Value right = stmt.getRightOp();


            if(right instanceof CaughtExceptionRef && left instanceof Local)
            {
                caught = (Local) left;
            }
            else
            {
                throw new RuntimeException("Unexpected start of exception handler: " + handlerUnit);
            }
        }

        /* simple fact for Paddle compatibility mode. Makes no sense
           to have this be flow sensitive (i.e., add instruction
           information), when it doesn't store instruction begin/end
           indices. The expectation is that this predicate will only
           be used for flow-insensitive analyses.
        */
        _db.add(SIMPLE_EXCEPTION_HANDLER,
                _db.asEntity(_rep.type(exc)),
                _db.addEntity(VAR,_rep.local(m, caught)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));

        String rep = _rep.handler(m, handler, session);
        _db.add(EXCEPTION_HANDLER,
                _db.asEntity(rep),
                // method
                _db.asEntity(_rep.method(m)),
                // handler index
                _db.asIntColumn(String.valueOf(session.getUnitNumber(handler.getHandlerUnit()))),
                // type
                _db.asEntity(_rep.type(exc)),
                // formal param
                _db.asEntity(_rep.local(m, caught)),
                // begin
                _db.asIntColumn(String.valueOf(session.getUnitNumber(handler.getBeginUnit()))),
                // end
                _db.asIntColumn(String.valueOf(session.getUnitNumber(handler.getEndUnit()))));
    }

    public void writeThisVar(SootMethod m)
    {
        _db.add(THIS_VAR,
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)),
                _db.addEntity(VAR, _rep.thisVar(m)));

        _db.add(VAR_TYPE,
                _db.asEntity(_rep.thisVar(m)),
                writeType(m.getDeclaringClass()));

        _db.add(VAR_DECLARING_METHOD,
                _db.asEntity(_rep.thisVar(m)),
                _db.asEntity(_rep.method(m)));
    }

    public void writeMethodDeclaresException(SootMethod m, SootClass exception)
    {
        _db.add(METHOD_DECL_EXCEPTION,
                writeType(exception),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public void writeFormalParam(SootMethod m, int i)
    {
        _db.add(FORMAL_PARAM,
                _db.asIntColumn(_rep.index(i)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)),
                _db.addEntity(VAR, _rep.param(m, i)));

        _db.add(VAR_TYPE,
                _db.asEntity(_rep.param(m, i)),
                writeType(m.getParameterType(i)));

        _db.add(VAR_DECLARING_METHOD,
                _db.asEntity(_rep.param(m, i)),
                _db.asEntity(_rep.method(m)));
    }

    public void writeLocal(SootMethod m, Local l)
    {
        _db.add(VAR_TYPE,
                _db.addEntity(VAR, _rep.local(m, l)),
                writeType(l.getType()));

        _db.add(VAR_DECLARING_METHOD,
                _db.asEntity(_rep.local(m, l)),
                _db.asEntity(METHOD_SIGNATURE, _rep.method(m)));
    }

    public Local writeStringConstantExpression(SootMethod inMethod, Stmt stmt, StringConstant constant, Session session)
    {
        // introduce a new temporary variable
        String basename = "$stringconstant";
        String varname = basename + session.nextNumber(basename);
        Local l = new JimpleLocal(varname, RefType.v("java.lang.String"));
        writeLocal(inMethod, l);
        writeAssignStringConstant(inMethod, stmt, l, constant, session);
        return l;
    }

    public Local writeNullExpression(SootMethod inMethod, Stmt stmt, Type type, Session session)
    {
        // introduce a new temporary variable
        String basename = "$null";
        String varname = basename + session.nextNumber(basename);
        Local l = new JimpleLocal(varname, type);
        writeLocal(inMethod, l);
        writeAssignNull(inMethod, stmt, l, session);
        return l;
    }

    public Local writeNumConstantExpression(SootMethod inMethod, Stmt stmt, NumericConstant constant, Session session)
    {
        // introduce a new temporary variable
        String basename = "$numconstant";
        String varname = basename + session.nextNumber(basename);
        Local l = new JimpleLocal(varname, constant.getType());
        writeLocal(inMethod, l);
        writeAssignNumConstant(inMethod, stmt, l, constant, session);
        return l;
    }

    public Local writeClassConstantExpression(SootMethod inMethod, Stmt stmt, ClassConstant constant, Session session)
    {
        // introduce a new temporary variable
        String basename = "$classconstant";
        String varname = basename + session.nextNumber(basename);
        Local l = new JimpleLocal(varname, RefType.v("java.lang.Class"));
        writeLocal(inMethod, l);
        writeAssignClassConstant(inMethod, stmt, l, constant, session);
        return l;
    }

    private void writeActualParams(SootMethod inMethod, Stmt stmt, InvokeExpr expr, String invokeExprRepr, Session session)
    {
        for(int i = 0; i < expr.getArgCount(); i++)
        {
            Value v = expr.getArg(i);

            if(v instanceof StringConstant)
            {
                v = writeStringConstantExpression(inMethod, stmt, (StringConstant) v, session);
            }
            else if(v instanceof ClassConstant)
            {
                v = writeClassConstantExpression(inMethod, stmt, (ClassConstant) v, session);
            }
            else if(v instanceof NumericConstant)
            {
                v = writeNumConstantExpression(inMethod, stmt, (NumericConstant) v, session);
            }
            else if(v instanceof NullConstant)
            {
                // Giving the type of the formal argument to be used in the creation of
                // temporary var for the actual argument (whose value is null).
                Type argType = expr.getMethodRef().parameterType(i);
                v = writeNullExpression(inMethod, stmt, argType, session);
            }

            if(v instanceof Local)
            {
                Local l = (Local) v;
                _db.add(ACTUAL_PARAMETER,
                        _db.asIntColumn(_rep.index(i)),
                        _db.asEntity(invokeExprRepr),
                        _db.addEntity(VAR, _rep.local(inMethod, l)));
            }
            else
            {
                throw new RuntimeException("Unknown actual parameter: " + v + " " + v.getClass());
            }
        }
    }

    public void writeInvoke(SootMethod inMethod, Stmt stmt, InvokeExpr expr, Session session)
    {
        writeInvokeHelper(inMethod, stmt, expr, session);
    }

    private String writeInvokeHelper(SootMethod inMethod, Stmt stmt, InvokeExpr expr, Session session)
    {
        String rep = _rep.invoke(inMethod, expr, session);
        writeActualParams(inMethod, stmt, expr, rep, session);

        LineNumberTag tag = (LineNumberTag) stmt.getTag("LineNumberTag");
        if(tag != null)
        {
            _db.add(METHOD_INV_LINENUM,
                    _db.asEntity(rep),
                    _db.asIntColumn(String.valueOf(tag.getLineNumber())));
        }

        Column index = _db.asIntColumn(String.valueOf(session.calcUnitNumber(stmt)));

        if(expr instanceof StaticInvokeExpr)
        {
            _db.add(STATIC_METHOD_INV,
                    _db.asEntity(rep),
                    index,
                    _db.asEntity(_rep.signature(expr.getMethod())),
                    _db.asEntity(_rep.method(inMethod)));
        }
        else if(expr instanceof VirtualInvokeExpr || expr instanceof InterfaceInvokeExpr)
        {
            _db.add(VIRTUAL_METHOD_INV,
                    _db.asEntity(rep),
                    index,
                    // signature
                    _db.asEntity(_rep.signature(expr.getMethod())),
                    // base
                    _db.addEntity(VAR, _rep.local(inMethod, (Local) ((InstanceInvokeExpr) expr).getBase())),
                    // in
                    _db.asEntity(_rep.method(inMethod)));
        }
        else if(expr instanceof SpecialInvokeExpr)
        {
            _db.add(SPECIAL_METHOD_INV,
                    _db.asEntity(rep),
                    index,
                    // signature
                    _db.asEntity(_rep.signature(expr.getMethod())),
                    // base
                    _db.addEntity(VAR, _rep.local(inMethod, (Local) ((InstanceInvokeExpr) expr).getBase())),
                    // in
                    _db.asEntity(_rep.method(inMethod)));
        }
        else
        {
            throw new RuntimeException("Cannot handle invoke expr: " + expr);
        }

        return rep;
    }

    private Value writeImmediate(SootMethod inMethod, Stmt stmt, Value v, Session session)
    {
        if(v instanceof StringConstant)
        {
            v = writeStringConstantExpression(inMethod, stmt, (StringConstant) v, session);
        }
        else if(v instanceof ClassConstant)
        {
            v = writeClassConstantExpression(inMethod, stmt, (ClassConstant) v, session);
        }
        else if(v instanceof NumericConstant)
        {
            v = writeNumConstantExpression(inMethod, stmt, (NumericConstant) v, session);
        }

        return v;
    }

}
