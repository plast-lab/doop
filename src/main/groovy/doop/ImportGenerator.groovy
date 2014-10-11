package doop

/**
 * The fact declarations import generator.
 * Mimics the behavior of the gen-import script.
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 11/10/2014
 */
class ImportGenerator {

    static enum Type {
        CSV,
        TEXT
    }

    private final Type type
    private final Writer w

    ImportGenerator(Type type, Writer w) {
        this.type = type
        this.w = w
    }

    void generate() {

        if (type==Type.TEXT) {
            w.write """option,delimeter,"\t"\n"""
            w.write "option,hasColumnNames,false\n"
        }
        else {
            w.write """option,delimeter,","\n"""
            w.write "option,hasColumnNames,false\n"
            w.write "option,quotedValues,true\n"
            w.write "option,escapeQuotedValues,true\n"
        }

        // entities
        writeImport "PrimitiveType", 1
        writeImport "ClassType", 1
        writeImport "ArrayType", 1
        writeImport "InterfaceType", 1
        writeImport "NullType", 1
        writeImport "ComponentType", 2

        // relations (TODO bring entities forward)
        writeImport "StringConstant", 1
        // writeImport ReifiedClass 2
        writeImport "HeapAllocation:Type", 2
        writeImport "AssignHeapAllocation", 3

        writeImport "ActualParam", 3
        writeImport "AssignLocal", 3
        writeImport "AssignCast", 4
        writeImport "AssignReturnValue", 2
        writeImport "DirectSuperinterface", 2
        writeImport "DirectSuperclass", 2
        writeImport "FieldSignature", 4
        writeImport "FieldModifier", 2
        writeImport "FormalParam", 3
        writeImport "LoadInstanceField", 4
        writeImport "LoadArrayIndex", 3
        writeImport "LoadStaticField", 3
        writeImport "LoadPrimStaticField", 2

        writeImport "MethodDeclaration", 2
        writeImport "MethodDeclaration:Exception", 2
        writeImport "MethodModifier", 2
        writeImport "MethodSignature:SimpleName", 2
        writeImport "MethodSignature:Descriptor", 2
        writeImport "MethodSignature:Type", 2

        writeImport "ReturnVar", 2

        writeImport "SpecialMethodInvocation:Base", 2
        writeImport "SpecialMethodInvocation:Signature", 2
        writeImport "SpecialMethodInvocation:In", 2

        writeImport "VirtualMethodInvocation:Base", 2
        writeImport "VirtualMethodInvocation:Signature", 2
        writeImport "VirtualMethodInvocation:In", 2
        writeImport "VirtualMethodInvocation", 3 // TODO eliminate

        writeImport "StaticMethodInvocation", 3 // TODO eliminate
        writeImport "StaticMethodInvocation:In", 2
        writeImport "StaticMethodInvocation:Signature", 2

        writeImport "StoreInstanceField", 4
        writeImport "StoreArrayIndex", 3
        writeImport "StoreStaticField", 3
        writeImport "StorePrimStaticField", 2

        writeImport "Var:Type", 2
        writeImport "Var:DeclaringMethod", 2

        writeImport "ApplicationClass", 1

        writeImport "ThisVar", 2
        writeImport "Throw", 2
        writeImport "Throw:Method", 2

        writeImport "SimpleExceptionHandler", 3
        writeImport "ExceptionHandler:Method", 2
        writeImport "ExceptionHandler:Type", 2
        writeImport "ExceptionHandler:FormalParam", 2
        writeImport "ExceptionHandler:Begin", 2
        writeImport "ExceptionHandler:End", 2
        writeImport "ExceptionHandler:Previous", 2

        writeImport "Instruction:Index", 2
        writeImport "Properties", 3

        /*
        writeImport ClassConstant 1
        writeImport ExceptionHandlerRef 1
        writeImport FieldSignatureRef 1
        writeImport MethodDescriptorRef 1
        writeImport MethodInvocationRef 1
        writeImport MethodSignatureRef 1
        writeImport ModifierRef 1
        writeImport NormalHeapAllocationRef 1
        writeImport SimpleNameRef 1
        writeImport ThrowRef 1
        writeImport VarRef 1
        */
    }

    protected void writeImport(String predicate, int arity) {
        String fileName = predicate.replaceAll(":", "-")
        String file = "facts/${fileName}.facts"
        w.write "fromFile,\"$file\""
        arity.times { int i ->
            w.write ",column:$i,$predicate:$i"
        }
        w.write "\n"
        w.write "toPredicate,$predicate"
        arity.times { int i ->
            w.write ",$predicate:$i"
        }
        w.write "\n"
    }

}
