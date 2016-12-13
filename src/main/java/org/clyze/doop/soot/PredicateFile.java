package org.clyze.doop.soot;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

enum PredicateFile
{
    ANDROID_ENTRY_POINT("AndroidEntryPoint"),
    ACTIVITY("Activity"),
    SERVICE("Service"),
    CONTENT_PROVIDER("ContentProvider"),
    BROADCAST_RECEIVER("BroadcastReceiver"),
    CALLBACK_METHOD("CallbackMethod"),
    LAYOUT_CONTROL("LayoutControl"),
    CLASS_TYPE("ClassType"),
    CLASS_MODIFIER("ClassModifier"),
    ARRAY_TYPE("ArrayType"),
    INTERFACE_TYPE("InterfaceType"),
    COMPONENT_TYPE("ComponentType"),
    ACTUAL_PARAMETER("ActualParam"),
    DIRECT_SUPER_IFACE("DirectSuperinterface"),
    DIRECT_SUPER_CLASS("DirectSuperclass"),
    FIELD_MODIFIER("Field-Modifier"),
    FORMAL_PARAM("FormalParam"),
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
    EMPTY_ARRAY("EmptyArray"),
    CLASS_HEAP("ClassHeap"),
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
    ASSIGN_OPER_TYPE("AssignOperType"),
    SPECIAL_METHOD_INV("SpecialMethodInvocation"),
    IF_VAR("IfVar"),
    VIRTUAL_METHOD_INV("VirtualMethodInvocation"),
    THROW("Throw"),
    THROW_NULL("ThrowNull"),
    EXCEPTION_HANDLER("ExceptionHandler"),
    METHOD("Method"),
    STORE_INST_FIELD("StoreInstanceField"),
    LOAD_INST_FIELD("LoadInstanceField"),
    STORE_STATIC_FIELD("StoreStaticField"),
    LOAD_STATIC_FIELD("LoadStaticField"),
    STORE_ARRAY_INDEX("StoreArrayIndex"),
    LOAD_ARRAY_INDEX("LoadArrayIndex"),
    ARRAY_INSN_INDEX("ArrayInsnIndex"),
    GOTO("Goto"),
    IF("If"),
    TABLE_SWITCH("TableSwitch"),
    TABLE_SWITCH_TARGET("TableSwitch-Target"),
    TABLE_SWITCH_DEFAULT("TableSwitch-Default"),
    LOOKUP_SWITCH("LookupSwitch"),
    LOOKUP_SWITCH_TARGET("LookupSwitch-Target"),
    LOOKUP_SWITCH_DEFAULT("LookupSwitch-Default"),
    RETURN("Return"),
    RETURN_VOID("ReturnVoid"),
    ASSIGN_PHANTOM_INVOKE("AssignPhantomInvoke"),
    PHANTOM_INVOKE("PhantomInvoke"),
    BREAKPOINT_STMT("BreakpointStmt"),
    UNSUPPORTED_INSTRUCTION("UnsupportedInstruction");

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
