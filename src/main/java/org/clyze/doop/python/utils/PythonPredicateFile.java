package org.clyze.doop.python.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public enum PythonPredicateFile
{
    FIELD_INITIAL_VALUE("FieldInitialValue"),
    CLASS_ANNOTATION("Class-Annotation"),
    CLASS_TYPE("ClassType"),
    CLASS_MODIFIER("ClassModifier"),
    ARRAY_TYPE("ArrayType"),
    INTERFACE_TYPE("InterfaceType"),
    GLOBAL_READ("GlobalRead"),
    GLOBAL_WRITE("GlobalWrite"),
    LEXICAL_READ("LexicalRead"),
    LEXICAL_WRITE("LexicalWrite"),
    ACTUAL_POSITIONAL_PARAMETER("ActualPositionalParam"),
    ACTUAL_KEYWORD_PARAMETER("ActualKeywordParam"),
    BOOTSTRAP_PARAMETER("BootstrapParam"),
    DIRECT_SUPER_IFACE("DirectSuperinterface"),
    DIRECT_SUPER_CLASS("DirectSuperclass"),
    FIELD_ANNOTATION("Field-Annotation"),
    FIELD_MODIFIER("Field-Modifier"),
    FORMAL_PARAM("FormalParam"),
    PARAM_ANNOTATION("Param-Annotation"),
    METHOD_DECL_EXCEPTION("Method-DeclaresException"),
    METHOD_MODIFIER("Method-Modifier"),
    NATIVE_RETURN_VAR("NativeReturnVar"),
    VAR_TYPE("Var-Type"),
    VAR_DECLARING_METHOD("Var-DeclaringMethod"),
    APP_CLASS("ApplicationClass"),
    THIS_VAR("ThisVar"),
    EXCEPT_HANDLER_PREV("ExceptionHandler-Previous"),
    ASSIGN_RETURN_VALUE("AssignReturnValue"),
    PROPERTIES("Properties"),
    ASSIGN_LOCAL("AssignLocal"),
    ASSIGN_CAST("AssignCast"),
    ASSIGN_CAST_NUM_CONST("AssignCastNumConstant"),
    ASSIGN_CAST_NULL("AssignCastNull"),
    ASSIGN_HEAP_ALLOC("AssignHeapAllocation"),
    ASSIGN_NUM_CONST("AssignNumConstant"),
    ASSIGN_NULL("AssignNull"),
    ASSIGN_INSTANCE_OF("AssignInstanceOf"),
    NORMAL_HEAP("NormalHeap"),
    CLASS_HEAP("ClassHeap"),
    METHOD_HANDLE_CONSTANT("MethodHandleConstant"),
    METHOD_TYPE_CONSTANT("MethodTypeConstant"),
    STRING_CONST("StringConstant"),
    STRING_RAW("StringRaw"),
    FIELD_SIGNATURE("Field"),
    ENTER_MONITOR("EnterMonitor"),
    EXIT_MONITOR("ExitMonitor"),
    METHOD_INV_LINE("MethodInvocation-Line"),
    STATIC_METHOD_INV("StaticMethodInvocation"),
    ASSIGN_BINOP("AssignBinop"),
    ASSIGN_UNOP("AssignUnop"),
    ASSIGN_OPER_FROM("AssignOperFrom"),
    SPECIAL_METHOD_INV("SpecialMethodInvocation"),
    IF_VAR("IfVar"),
    VIRTUAL_METHOD_INV("VirtualMethodInvocation"),
    DYNAMIC_METHOD_INV("DynamicMethodInvocation"),
    THROW("Throw"),
    THROW_NULL("ThrowNull"),
    EXCEPTION_HANDLER("ExceptionHandler"),
    METHOD("Method"),
    FUNCTION("Function"),
    FUNCTION_INVOCATION("FunctionInvocation"),
    METHOD_ANNOTATION("Method-Annotation"),
    STORE_INST_FIELD("StoreInstanceField"),
    LOAD_INST_FIELD("LoadInstanceField"),
    STORE_STATIC_FIELD("StoreStaticField"),
    LOAD_STATIC_FIELD("LoadStaticField"),
    GOTO("Goto"),
    IF("If"),
    LOOKUP_SWITCH("LookupSwitch"),
    LOOKUP_SWITCH_TARGET("LookupSwitch-Target"),
    LOOKUP_SWITCH_DEFAULT("LookupSwitch-Default"),
    RETURN("Return"),
    RETURN_VOID("ReturnVoid"),
    ASSIGN_PHANTOM_INVOKE("AssignPhantomInvoke"),
    PHANTOM_METHOD("PhantomMethod"),
    PHANTOM_BASED_METHOD("PhantomBasedMethod"),
    PHANTOM_TYPE("PhantomType"),
    BREAKPOINT_STMT("BreakpointStmt"),
    UNSUPPORTED_INSTRUCTION("UnsupportedInstruction");

    private final String name;

    PythonPredicateFile(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return  name;
    }

    public Writer getWriter(File directory, String suffix) throws IOException {
        File factsFile = new File(directory, name + suffix);
        FileUtils.touch(factsFile);
        return new FileWriter(factsFile, true);
    }
}
