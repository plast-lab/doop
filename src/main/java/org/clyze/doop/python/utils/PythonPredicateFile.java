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
    PRINT_STATEMENT("PrintStatement"),
    PRINT_ARG("PrintArg"),
    YIELD_STATEMENT("YieldStatement"),
    YIELD_ARG("YieldArg"),
    ITERATOR_GET_NEXT_PROPERTY_NAME("IteratorGetNextPropertyName"),
    ACTUAL_POSITIONAL_PARAMETER("ActualPositionalParam"),
    ACTUAL_KEYWORD_PARAMETER("ActualKeywordParam"),
    DIRECT_SUPER_CLASS("DirectSuperclass"),
    FORMAL_PARAM("FormalParam"),
    FORMAL_PARAM_DEFAULT_VAR("FormalParam-DefaultValueVar"),
    PARAM_ANNOTATION("Param-Annotation"),
    NATIVE_RETURN_VAR("NativeReturnVar"),
    VAR_DECLARING_FUNCTION("Var-DeclaringFunction"),
    VAR_SOURCE_NAME("Var-SourceName"),
    INSTRUCTION_SOURCE_POSITION("Instruction-SourcePosition"),
    APP_CLASS("ApplicationClass"),
    THIS_VAR("ThisVar"),
    EXCEPT_HANDLER_PREV("ExceptionHandler-Previous"),
    ASSIGN_RETURN_VALUE("AssignReturnValue"),
    ASSIGN_LOCAL("AssignLocal"),
    ASSIGN_HEAP_ALLOC("AssignHeapAllocation"),
    ASSIGN_BOOL_CONST("AssignBoolConstant"),
    ASSIGN_INT_CONST("AssignIntConstant"),
    ASSIGN_FLOAT_CONST("AssignFloatConstant"),
    ASSIGN_NONE("AssignNone"),
    NORMAL_HEAP("NormalHeap"),
    STRING_CONST("StringConstant"),
    STRING_RAW("StringRaw"),
    FIELD_SIGNATURE("Field"),
    FUNCTION_INV_LINE("FunctionInvocation-Line"),
    FUNCTION_INV_TOTAL_PARAMS("FunctionInvocation-TotalParams"),
    FUNCTION_INV("FunctionInvocation"),
    ASSIGN_BINOP("AssignBinop"),
    ASSIGN_UNOP("AssignUnop"),
    ASSIGN_OPER_FROM("AssignOperFrom"),
    IF_VAR("IfVar"),
    THROW("Throw"),
    THROW_NULL("ThrowNull"),
    EXCEPTION_HANDLER("ExceptionHandler"),
    FILE_DECLAREDING_PACKAGE("File-DeclaringPackage"),
    PACKAGE_DECLAREDING_PACKAGE("Package-DeclaringPackage"),
    PROJECT_ROOT_FOLDER("Project-RootFolder"),
    FUNCTION("Function"),
    GLOBAL_FUNCTION("GlobalFunction"),
    FUNCTION_SOURCE_POSITION("Function-SourcePosition"),
    COMPREHENSION_FUNCTION("ComprehensionFunction"),
    FUNCTION_DECORATOR("Function-Decorator"),
    STORE_INST_FIELD("StoreInstanceField"),
    LOAD_INST_FIELD("LoadInstanceField"),
    GOTO("Goto"),
    IF("If"),
    RETURN("Return"),
    RETURN_NONE("ReturnNone"),
    BREAKPOINT_STMT("BreakpointStmt"),
    ORIGINAL_INT_CONSTANT("OriginalIntConstant"),
    UNSUPPORTED_INSTRUCTION("UnsupportedInstruction"),
    EMPTY_CHA("EmptyCha"),
    ERROR_OR_EXCEPTON("ErrorOrException");

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
