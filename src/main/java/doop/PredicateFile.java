package doop;

import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

public enum PredicateFile
{
    PRIMITIVE_TYPE("PrimitiveType", ImportScheme.BY_IMPORT_SCRIPT),
    CLASS_TYPE("ClassType", ImportScheme.BY_IMPORT_SCRIPT),
    ARRAY_TYPE("ArrayType", ImportScheme.BY_IMPORT_SCRIPT),
    INTERFACE_TYPE("InterfaceType", ImportScheme.BY_IMPORT_SCRIPT),
    NULL_TYPE("NullType", ImportScheme.BY_IMPORT_SCRIPT),
    COMPONENT_TYPE("ComponentType", ImportScheme.BY_IMPORT_SCRIPT),
    ACTUAL_PARAMETER("ActualParam", ImportScheme.BY_IMPORT_SCRIPT),
    DIRECT_SUPER_IFACE("DirectSuperinterface", ImportScheme.BY_IMPORT_SCRIPT),
    DIRECT_SUPER_CLASS("DirectSuperclass", ImportScheme.BY_IMPORT_SCRIPT),
    FIELD_MODIFIER("FieldModifier", ImportScheme.BY_IMPORT_SCRIPT),
    FORMAL_PARAM("FormalParam", ImportScheme.BY_IMPORT_SCRIPT),
    METHOD_DECL("MethodDeclaration", ImportScheme.BY_IMPORT_SCRIPT),
    METHOD_DECL_EXCEPTION("MethodDeclaration-Exception", ImportScheme.BY_IMPORT_SCRIPT),
    METHOD_MODIFIER("MethodModifier", ImportScheme.BY_IMPORT_SCRIPT),
    NATIVE_RETURN_VAR("NativeReturnVar", ImportScheme.BY_IMPORT_SCRIPT),
    VAR("Var", ImportScheme.IMPLICIT),
    VAR_TYPE("Var-Type", ImportScheme.BY_IMPORT_SCRIPT),
    VAR_DECLARING_METHOD("Var-DeclaringMethod", ImportScheme.BY_IMPORT_SCRIPT),
    APP_CLASS("ApplicationClass", ImportScheme.BY_IMPORT_SCRIPT),
    THIS_VAR("ThisVar", ImportScheme.BY_IMPORT_SCRIPT),
    SIMPLE_EXCEPTION_HANDLER("SimpleExceptionHandler", ImportScheme.BY_IMPORT_SCRIPT),
    EXCEPT_HANDLER_PREV("ExceptionHandler-Previous", ImportScheme.BY_IMPORT_SCRIPT),
    ASSIGN_RETURN_VALUE("AssignReturnValue", ImportScheme.BY_IMPORT_SCRIPT),
    PROPERTIES("Properties", ImportScheme.BY_IMPORT_SCRIPT),
    ASSIGN_LOCAL("AssignLocal", ImportScheme.BY_FILE_PREDICATE),
    ASSIGN_CAST("AssignCast", ImportScheme.BY_FILE_PREDICATE),
    ASSIGN_HEAP_ALLOC("AssignHeapAllocation", ImportScheme.BY_FILE_PREDICATE),
    ASSIGN_MULT_ARRY_ALLOC("AssignMultiArrayAllocation", ImportScheme.BY_FILE_PREDICATE),
    ASSIGN_NUM_CONST("AssignNumConstant", ImportScheme.BY_FILE_PREDICATE),
    ASSIGN_NULL("AssignNull", ImportScheme.BY_FILE_PREDICATE),
    NORMAL_OBJ("NormalObject", ImportScheme.BY_FILE_PREDICATE),
    EMPTY_ARRAY("EmptyArray", ImportScheme.BY_FILE_PREDICATE),
    CLASS_OBJ("ClassObject", ImportScheme.BY_FILE_PREDICATE),
    STRING_CONST("StringConstant", ImportScheme.BY_FILE_PREDICATE),
    FIELD_SIGNATURE("FieldSignature", ImportScheme.BY_FILE_PREDICATE),
    ENTER_MONITOR("EnterMonitor", ImportScheme.BY_FILE_PREDICATE),
    EXIT_MONITOR("ExitMonitor", ImportScheme.BY_FILE_PREDICATE),
    STATIC_METHOD_INV("StaticMethodInvocation", ImportScheme.BY_FILE_PREDICATE),
    SPECIAL_METHOD_INV("SpecialMethodInvocation", ImportScheme.BY_FILE_PREDICATE),
    VIRTUAL_METHOD_INV("VirtualMethodInvocation", ImportScheme.BY_FILE_PREDICATE),
    THROW("Throw", ImportScheme.BY_FILE_PREDICATE),
    EXCEPTION_HANDLER("ExceptionHandler", ImportScheme.BY_FILE_PREDICATE),
    METHOD_SIGNATURE("MethodSignature", ImportScheme.BY_FILE_PREDICATE),
    STORE_INST_FIELD("StoreInstanceField", ImportScheme.BY_FILE_PREDICATE),
    LOAD_INST_FIELD("LoadInstanceField", ImportScheme.BY_FILE_PREDICATE),
    STORE_STATIC_FIELD("StoreStaticField", ImportScheme.BY_FILE_PREDICATE),
    LOAD_STATIC_FIELD("LoadStaticField", ImportScheme.BY_FILE_PREDICATE),
    STORE_ARRAY_INDEX("StoreArrayIndex", ImportScheme.BY_FILE_PREDICATE),
    LOAD_ARRAY_INDEX("LoadArrayIndex", ImportScheme.BY_FILE_PREDICATE),
    GOTO("Goto", ImportScheme.BY_FILE_PREDICATE),
    IF("If", ImportScheme.BY_FILE_PREDICATE),
    TABLE_SWITCH("TableSwitch", ImportScheme.BY_FILE_PREDICATE),
    TABLE_SWITCH_TARGET("TableSwitch-Target", ImportScheme.BY_FILE_PREDICATE),
    TABLE_SWITCH_DEFAULT("TableSwitch-Default", ImportScheme.BY_FILE_PREDICATE),
    LOOKUP_SWITCH("LookupSwitch", ImportScheme.BY_FILE_PREDICATE),
    LOOKUP_SWITCH_TARGET("LookupSwitch-Target", ImportScheme.BY_FILE_PREDICATE),
    LOOKUP_SWITCH_DEFAULT("LookupSwitch-Default", ImportScheme.BY_FILE_PREDICATE),
    RETURN("Return", ImportScheme.BY_FILE_PREDICATE),
    RETURN_VOID("ReturnVoid", ImportScheme.BY_FILE_PREDICATE),
    UNSUPPORTED_INSTRUCTION("UnsupportedInstruction", ImportScheme.BY_FILE_PREDICATE),
    SIMPLE_NAME("SimpleName", ImportScheme.IMPLICIT),
    MODIFIER("Modifier", ImportScheme.IMPLICIT);

    private final String name;
    private final ImportScheme importScheme;

    private PredicateFile(String name, ImportScheme scheme)
    {
        this.name = name;
        this.importScheme = scheme;
    }

    @Override
    public String toString()
    {
        return  name;
    }

    public Writer getWriter(File directory, String suffix) throws IOException
    {
        return new FileWriter(new File(directory, name + suffix));
    }

    public ImportScheme getImportScheme() {
        return importScheme;
    }

    public static enum ImportScheme {
        BY_IMPORT_SCRIPT, BY_FILE_PREDICATE, IMPLICIT;
    };
}
