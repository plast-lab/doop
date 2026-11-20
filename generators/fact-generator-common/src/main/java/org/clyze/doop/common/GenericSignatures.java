package org.clyze.doop.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

/**
 * Parses the generic signatures of classes, fields and methods (the
 * Signature attribute, JVMS 4.7.9.1) into type expressions, using
 * ASM's signature reader.
 */
public final class GenericSignatures {

    private GenericSignatures() {}

    /** A type as written in a signature. */
    public abstract static class TypeExpr {}

    /** A primitive type or void ("int"). */
    public static final class BaseType extends TypeExpr {
        public final String name;
        BaseType(String name) { this.name = name; }
    }

    /** A use of a type variable ("T"). */
    public static final class TypeVar extends TypeExpr {
        public final String name;
        TypeVar(String name) { this.name = name; }
    }

    /** An array type ("T[]", "java.util.List&lt;X&gt;[]"). */
    public static final class ArrayOf extends TypeExpr {
        public final TypeExpr component;
        ArrayOf(TypeExpr component) { this.component = component; }
    }

    /**
     * A class or interface type with its type arguments. Inner classes
     * of generic classes have one segment per class ("Outer&lt;A&gt;.Inner&lt;B&gt;").
     */
    public static final class ClassOf extends TypeExpr {
        public final List<Segment> segments;
        ClassOf(List<Segment> segments) { this.segments = segments; }
    }

    /** One class in a class type: its name and its type arguments. */
    public static final class Segment {
        public final String name;
        public final List<TypeArg> args;
        Segment(String name, List<TypeArg> args) { this.name = name; this.args = args; }
    }

    /**
     * A type argument. The bound is "=" (exactly the type), "extends"
     * ("? extends T"), "super" ("? super T") or "*" ("?", no type).
     */
    public static final class TypeArg {
        public final String bound;
        public final TypeExpr type;
        TypeArg(String bound, TypeExpr type) { this.bound = bound; this.type = type; }
    }

    /** A declared type parameter ("T extends Comparable&lt;T&gt;") with its bounds in order. */
    public static final class TypeParam {
        public final String name;
        public final List<TypeExpr> bounds;
        TypeParam(String name, List<TypeExpr> bounds) { this.name = name; this.bounds = bounds; }
    }

    /** A parsed signature; only the parts of its kind (class, method or field) are set. */
    public static final class Signature {
        public final List<TypeParam> typeParams = new ArrayList<>();
        public TypeExpr superclass;
        public final List<TypeExpr> interfaces = new ArrayList<>();
        public final List<TypeExpr> params = new ArrayList<>();
        public TypeExpr returnType;
        public TypeExpr fieldType;
    }

    public static Signature parseClass(String signature) {
        Signature sig = new Signature();
        DeclarationBuilder b = new DeclarationBuilder();
        new SignatureReader(signature).accept(b);
        b.fill(sig);
        return sig;
    }

    public static Signature parseMethod(String signature) {
        return parseClass(signature);
    }

    public static Signature parseField(String signature) {
        Signature sig = new Signature();
        TypeBuilder b = new TypeBuilder();
        new SignatureReader(signature).acceptType(b);
        sig.fieldType = b.get();
        return sig;
    }

    /** Visits the top level of a class or method signature. */
    private static final class DeclarationBuilder extends SignatureVisitor {
        private final List<String> paramNames = new ArrayList<>();
        private final List<List<TypeBuilder>> paramBounds = new ArrayList<>();
        private TypeBuilder superclass;
        private final List<TypeBuilder> interfaces = new ArrayList<>();
        private final List<TypeBuilder> params = new ArrayList<>();
        private TypeBuilder returnType;

        DeclarationBuilder() { super(Opcodes.ASM9); }

        @Override
        public void visitFormalTypeParameter(String name) {
            paramNames.add(name);
            paramBounds.add(new ArrayList<>());
        }

        private SignatureVisitor bound() {
            TypeBuilder b = new TypeBuilder();
            paramBounds.get(paramBounds.size() - 1).add(b);
            return b;
        }

        @Override
        public SignatureVisitor visitClassBound() { return bound(); }

        @Override
        public SignatureVisitor visitInterfaceBound() { return bound(); }

        @Override
        public SignatureVisitor visitSuperclass() {
            superclass = new TypeBuilder();
            return superclass;
        }

        @Override
        public SignatureVisitor visitInterface() {
            TypeBuilder b = new TypeBuilder();
            interfaces.add(b);
            return b;
        }

        @Override
        public SignatureVisitor visitParameterType() {
            TypeBuilder b = new TypeBuilder();
            params.add(b);
            return b;
        }

        @Override
        public SignatureVisitor visitReturnType() {
            returnType = new TypeBuilder();
            return returnType;
        }

        @Override
        public SignatureVisitor visitExceptionType() {
            // Thrown types are not written.
            return new TypeBuilder();
        }

        void fill(Signature sig) {
            for (int i = 0; i < paramNames.size(); i++) {
                List<TypeExpr> bounds = new ArrayList<>();
                for (TypeBuilder b : paramBounds.get(i))
                    bounds.add(b.get());
                sig.typeParams.add(new TypeParam(paramNames.get(i), bounds));
            }
            if (superclass != null)
                sig.superclass = superclass.get();
            for (TypeBuilder b : interfaces)
                sig.interfaces.add(b.get());
            for (TypeBuilder b : params)
                sig.params.add(b.get());
            if (returnType != null)
                sig.returnType = returnType.get();
        }
    }

    /** Builds one type; the result is available after the reader has visited it. */
    private static final class TypeBuilder extends SignatureVisitor {
        private TypeExpr result;
        private TypeBuilder component;
        private List<String> segmentNames;
        private List<List<Object[]>> segmentArgs;   // each argument: {bound, TypeBuilder or null}

        TypeBuilder() { super(Opcodes.ASM9); }

        @Override
        public void visitBaseType(char descriptor) {
            result = new BaseType(baseTypeName(descriptor));
        }

        @Override
        public void visitTypeVariable(String name) {
            result = new TypeVar(name);
        }

        @Override
        public SignatureVisitor visitArrayType() {
            component = new TypeBuilder();
            return component;
        }

        @Override
        public void visitClassType(String name) {
            segmentNames = new ArrayList<>();
            segmentArgs = new ArrayList<>();
            startSegment(name.replace('/', '.'));
        }

        @Override
        public void visitInnerClassType(String name) {
            startSegment(name);
        }

        private void startSegment(String name) {
            segmentNames.add(name);
            segmentArgs.add(new ArrayList<>());
        }

        private List<Object[]> currentArgs() {
            return segmentArgs.get(segmentArgs.size() - 1);
        }

        @Override
        public void visitTypeArgument() {
            currentArgs().add(new Object[] { "*", null });
        }

        @Override
        public SignatureVisitor visitTypeArgument(char wildcard) {
            TypeBuilder b = new TypeBuilder();
            String bound;
            switch (wildcard) {
                case SignatureVisitor.EXTENDS: bound = "extends"; break;
                case SignatureVisitor.SUPER:   bound = "super"; break;
                default:                       bound = "=";
            }
            currentArgs().add(new Object[] { bound, b });
            return b;
        }

        TypeExpr get() {
            if (result != null)
                return result;
            if (component != null)
                return new ArrayOf(component.get());
            if (segmentNames != null) {
                List<Segment> segments = new ArrayList<>();
                for (int i = 0; i < segmentNames.size(); i++) {
                    List<TypeArg> args = new ArrayList<>();
                    for (Object[] a : segmentArgs.get(i)) {
                        TypeBuilder b = (TypeBuilder) a[1];
                        args.add(new TypeArg((String) a[0], b == null ? null : b.get()));
                    }
                    segments.add(new Segment(segmentNames.get(i), Collections.unmodifiableList(args)));
                }
                return new ClassOf(segments);
            }
            throw new IllegalStateException("incomplete type in signature");
        }
    }

    private static String baseTypeName(char descriptor) {
        switch (descriptor) {
            case 'Z': return "boolean";
            case 'B': return "byte";
            case 'C': return "char";
            case 'S': return "short";
            case 'I': return "int";
            case 'J': return "long";
            case 'F': return "float";
            case 'D': return "double";
            case 'V': return "void";
            default: throw new IllegalArgumentException("unknown base type: " + descriptor);
        }
    }
}
