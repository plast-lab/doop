package doop

/**
 * The fact declarations import generator.
 * Mimics the behavior of the writeImport script.
 *
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

        //LATEST: updated generation of imports

        // entities
        // writeImport "PrimitiveType", 1
        // writeImport "NullType", 1
        writeImport "ClassType", 1
        writeImport "ArrayType", 1
        writeImport "InterfaceType", 1

        // The following are auto-generated
        // writeImport "ExceptionHandler", 1
        // writeImport "FieldSignature", 1
        // writeImport "MethodDescriptor", 1
        // writeImport "MethodInvocation", 1
        // writeImport "MethodSignature", 1
        // writeImport "Modifier", 1
        // writeImport "SimpleName", 1
        // writeImport "Var", 1

        // relations (TODO bring entities forward)
        writeImport "ComponentType", 2
        writeImport "ActualParam", 3
        writeImport "DirectSuperinterface", 2
        writeImport "DirectSuperclass", 2
        writeImport "FieldModifier", 2
        writeImport "FormalParam", 3
        writeImport "MethodDeclaration", 2
        writeImport "MethodDeclaration:Exception", 2
        writeImport "MethodModifier", 2
        writeImport "NativeReturnVar", 2
        writeImport "Var:Type", 2
        writeImport "Var:DeclaringMethod", 2
        writeImport "ApplicationClass", 1
        writeImport "ThisVar", 2
        writeImport "SimpleExceptionHandler", 3
        writeImport "ExceptionHandler:Previous", 2
        writeImport "AssignReturnValue", 2
        writeImport "Properties", 3
    }

    protected void writeImport(String predicate, int arity) {
        String fileName = predicate.replaceAll(":", "-")
        String file = "facts/${fileName}.facts"
        w.write "fromFile,\"$file\""
        arity.times { int i ->
            w.write ",column:${i+1},$predicate:${i+1}"
        }
        w.write "\n"
        w.write "toPredicate,$predicate"
        arity.times { int i ->
            w.write ",$predicate:${i+1}"
        }
        w.write "\n"
    }

}
