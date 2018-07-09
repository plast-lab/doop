package org.clyze.doop.python.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public enum PythonPredicateFile
{
    CLASS_TYPE("ClassType"),
    IMPORT("Import"),
    GLOBAL_READ("GlobalRead"),
    GLOBAL_WRITE("GlobalWrite"),
    LEXICAL_READ("LexicalRead"),
    LEXICAL_WRITE("LexicalWrite"),
    REFLECTIVE_READ("ReflectiveRead"),
    REFLECTIVE_WRITE("ReflectiveWrite"),
    ITERATOR_GET_NEXT_PROPERTY_NAME("IteratorGetNextPropertyName"),
    ACTUAL_POSITIONAL_PARAMETER("ActualPositionalParam"),
    ACTUAL_KEYWORD_PARAMETER("ActualKeywordParam"),
    BOOTSTRAP_PARAMETER("BootstrapParam"),
    DIRECT_SUPER_CLASS("DirectSuperclass"),
    FORMAL_PARAM("FormalParam"),
    PARAM_ANNOTATION("Param-Annotation"),
    METHOD_DECL_EXCEPTION("Method-DeclaresException"),
    METHOD_MODIFIER("Method-Modifier"),
    NATIVE_RETURN_VAR("NativeReturnVar"),
    VAR_TYPE("Var-Type"),
    VAR_DECLARING_METHOD("Var-DeclaringMethod"),
    VAR_SOURCE_NAME("Var-SourceName"),
    APP_CLASS("ApplicationClass"),
    THIS_VAR("ThisVar"),
    EXCEPT_HANDLER_PREV("ExceptionHandler-Previous"),
    ASSIGN_RETURN_VALUE("AssignReturnValue"),
    ASSIGN_LOCAL("AssignLocal"),
    ASSIGN_HEAP_ALLOC("AssignHeapAllocation"),
    ASSIGN_NUM_CONST("AssignNumConstant"),
    ASSIGN_NULL("AssignNull"),
    ASSIGN_INSTANCE_OF("AssignInstanceOf"),
    NORMAL_HEAP("NormalHeap"),
    CLASS_HEAP("ClassHeap"),
    STRING_CONST("StringConstant"),
    STRING_RAW("StringRaw"),
    FIELD_SIGNATURE("Field"),
    ENTER_MONITOR("EnterMonitor"),
    EXIT_MONITOR("ExitMonitor"),
    METHOD_INV_LINE("MethodInvocation-Line"),
    STATIC_METHOD_INV("StaticMethodInvocation"),
    VIRTUAL_METHOD_INV("VirtualMethodInvocation"),
    ASSIGN_BINOP("AssignBinop"),
    ASSIGN_UNOP("AssignUnop"),
    ASSIGN_OPER_FROM("AssignOperFrom"),
    IF_VAR("IfVar"),
    THROW("Throw"),
    THROW_NULL("ThrowNull"),
    EXCEPTION_HANDLER("ExceptionHandler"),
    METHOD("Method"),
    FUNCTION("Function"),
    FUNCTION_INVOCATION("FunctionInvocation"),
    METHOD_ANNOTATION("Method-Annotation"),
    STORE_INST_FIELD("StoreInstanceField"),
    LOAD_INST_FIELD("LoadInstanceField"),
    GOTO("Goto"),
    IF("If"),
    RETURN("Return"),
    RETURN_VOID("ReturnVoid"),
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
