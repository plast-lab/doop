package org.clyze.doop.soot;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

enum PredicateFile
{
    ANDROID_ENTRY_POINT("_AndroidEntryPoint"),
    ACTIVITY("_Activity"),
    SERVICE("_Service"),
    CONTENT_PROVIDER("_ContentProvider"),
    BROADCAST_RECEIVER("_BroadcastReceiver"),
    CALLBACK_METHOD("_CallbackMethod"),
    LAYOUT_CONTROL("_LayoutControl"),
    FIELD_INITIAL_VALUE("_FieldInitialValue"),
    CLASS_TYPE("_ClassType"),
    CLASS_MODIFIER("_ClassModifier"),
    ARRAY_TYPE("_ArrayType"),
    INTERFACE_TYPE("_InterfaceType"),
    COMPONENT_TYPE("_ComponentType"),
    ACTUAL_PARAMETER("_ActualParam"),
    DIRECT_SUPER_IFACE("_DirectSuperinterface"),
    DIRECT_SUPER_CLASS("_DirectSuperclass"),
    FIELD_MODIFIER("_Field_Modifier"),
    FORMAL_PARAM("_FormalParam"),
    METHOD_DECL_EXCEPTION("_Method_DeclaresException"),
    METHOD_MODIFIER("_Method_Modifier"),
    NATIVE_RETURN_VAR("_NativeReturnVar"),
    VAR_TYPE("_Var_Type"),
    VAR_DECLARING_METHOD("_Var_DeclaringMethod"),
    APP_CLASS("_ApplicationClass"),
    THIS_VAR("_ThisVar"),
    EXCEPT_HANDLER_PREV("_ExceptionHandler_Previous"),
    ASSIGN_RETURN_VALUE("_AssignReturnValue"),
    PROPERTIES("_Properties"),
    ASSIGN_LOCAL("_AssignLocal"),
    ASSIGN_CAST("_AssignCast"),
    ASSIGN_CAST_NUM_CONST("_AssignCastNumConstant"),
    ASSIGN_CAST_NULL("_AssignCastNull"),
    ASSIGN_HEAP_ALLOC("_AssignHeapAllocation"),
    ASSIGN_NUM_CONST("_AssignNumConstant"),
    ASSIGN_NULL("_AssignNull"),
    ASSIGN_INSTANCE_OF("_AssignInstanceOf"),
    NORMAL_HEAP("_NormalHeap"),
    EMPTY_ARRAY("_EmptyArray"),
    CLASS_HEAP("_ClassHeap"),
    STRING_CONST("_StringConstant"),
    STRING_RAW("_StringRaw"),
    FIELD_SIGNATURE("_Field"),
    ENTER_MONITOR("_EnterMonitor"),
    EXIT_MONITOR("_ExitMonitor"),
    METHOD_INV_LINE("_MethodInvocation_Line"),
    STATIC_METHOD_INV("_StaticMethodInvocation"),
    ASSIGN_BINOP("_AssignBinop"),
    ASSIGN_UNOP("_AssignUnop"),
    ASSIGN_OPER_FROM("_AssignOperFrom"),
    ASSIGN_OPER_TYPE("_AssignOperType"),
    SPECIAL_METHOD_INV("_SpecialMethodInvocation"),
    IF_VAR("_IfVar"),
    VIRTUAL_METHOD_INV("_VirtualMethodInvocation"),
    THROW("_Throw"),
    THROW_NULL("_ThrowNull"),
    EXCEPTION_HANDLER("_ExceptionHandler"),
    METHOD("_Method"),
    STORE_INST_FIELD("_StoreInstanceField"),
    LOAD_INST_FIELD("_LoadInstanceField"),
    STORE_STATIC_FIELD("_StoreStaticField"),
    LOAD_STATIC_FIELD("_LoadStaticField"),
    STORE_ARRAY_INDEX("_StoreArrayIndex"),
    LOAD_ARRAY_INDEX("_LoadArrayIndex"),
    ARRAY_INSN_INDEX("_ArrayInsnIndex"),
    GOTO("_Goto"),
    IF("_If"),
    TABLE_SWITCH("_TableSwitch"),
    TABLE_SWITCH_TARGET("_TableSwitch_Target"),
    TABLE_SWITCH_DEFAULT("_TableSwitch_Default"),
    LOOKUP_SWITCH("_LookupSwitch"),
    LOOKUP_SWITCH_TARGET("_LookupSwitch_Target"),
    LOOKUP_SWITCH_DEFAULT("_LookupSwitch_Default"),
    RETURN("_Return"),
    RETURN_VOID("_ReturnVoid"),
    ASSIGN_PHANTOM_INVOKE("_AssignPhantomInvoke"),
    PHANTOM_INVOKE("_PhantomInvoke"),
    BREAKPOINT_STMT("_BreakpointStmt"),
    UNSUPPORTED_INSTRUCTION("_UnsupportedInstruction");

    private final String name;

    PredicateFile(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return  name;
    }

    public Writer getWriter(File directory, String suffix) throws IOException {
        File factsFile = new File(directory, name + suffix);
        FileUtils.touch(factsFile);
        return new FileWriter(factsFile);
    }
}
