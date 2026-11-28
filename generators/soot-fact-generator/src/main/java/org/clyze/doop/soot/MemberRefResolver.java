package org.clyze.doop.soot;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import java.util.TreeSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import soot.IFoundFile;
import soot.Scene;
import soot.SootClass;
import soot.SourceLocator;
import soot.Type;
import soot.asm.AsmUtil;

/**
 * Closes the Scene before bodies are built in parallel.
 *
 * <p>Resolving a field or method reference that has no matching member makes
 * Soot create one on the fly: a phantom member on the first phantom class of
 * the hierarchy, or an "Unresolved compilation error" method (a phantom field)
 * on the declaring class of the reference. Later lookups of related
 * references find these members or not, so when bodies are built on several
 * threads, which class a missing member ends up on depends on timing (dotCMS:
 * a Jetty 8 interface next to Jetty 9 classes gave different facts on two
 * runs).
 *
 * <p>This pass reads the field and method instructions of every class whose
 * bodies will be built, from the class file Soot loaded for it, and resolves
 * each distinct reference once, on one thread, in a fixed order. Every member
 * that body construction would create then exists before it starts, and body
 * construction only looks members up.
 */
final class MemberRefResolver {

    private MemberRefResolver() {}

    /** Number of methods and fields of all Scene classes. */
    static long memberCount(Scene scene) {
        long n = 0;
        for (SootClass c : new ArrayList<>(scene.getClasses()))
            if (c.resolvingLevel() >= SootClass.SIGNATURES)
                n += c.getMethodCount() + c.getFieldCount();
        return n;
    }

    /** All methods and fields of the Scene's classes, by identity. */
    static java.util.Set<Object> members(Scene scene) {
        java.util.Set<Object> all = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (SootClass c : new ArrayList<>(scene.getClasses()))
            if (c.resolvingLevel() >= SootClass.SIGNATURES) {
                all.addAll(c.getMethods());
                all.addAll(c.getFields());
            }
        return all;
    }

    /** Signatures of the members not in {@code before}, sorted. */
    static List<String> newMembers(Scene scene, java.util.Set<Object> before) {
        List<String> added = new ArrayList<>();
        for (SootClass c : new ArrayList<>(scene.getClasses()))
            if (c.resolvingLevel() >= SootClass.SIGNATURES) {
                for (soot.SootMethod m : c.getMethods())
                    if (!before.contains(m))
                        added.add(m.getSignature() + (c.isPhantom() ? " (phantom class)" : "") + (m.isPhantom() ? " phantom" : ""));
                for (soot.SootField f : c.getFields())
                    if (!before.contains(f))
                        added.add(f.getSignature() + (c.isPhantom() ? " (phantom class)" : "") + (f.isPhantom() ? " phantom" : ""));
            }
        java.util.Collections.sort(added);
        return added;
    }

    /**
     * Resolves the references of the given classes.
     *
     * @return {distinct references, references that could not be resolved}
     */
    static int[] resolveAll(Scene scene, Collection<SootClass> classes) {
        List<SootClass> sorted = new ArrayList<>(classes);
        sorted.sort(Comparator.comparing(SootClass::getName));

        // kind|owner|name|descriptor, kind: F/f (static/instance field), M/m (static/instance method)
        TreeSet<String> refs = new TreeSet<>();
        for (SootClass c : sorted) {
            if (c.isPhantom())
                continue;
            IFoundFile file = SourceLocator.v().lookupInClassPath(c.getName().replace('.', '/') + ".class");
            if (file == null)
                continue;
            try (InputStream in = file.inputStream()) {
                new ClassReader(in).accept(new RefCollector(refs), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            } catch (IOException | RuntimeException ex) {
                System.err.println("WARNING: cannot read the references of " + c.getName() + ": " + ex);
            } finally {
                file.close();
            }
        }

        int failed = 0;
        com.google.common.base.Optional<String> noModule = com.google.common.base.Optional.absent();
        for (String r : refs) {
            String[] p = r.split("\\|", 4);
            String ownerName = AsmUtil.toQualifiedName(p[1]);
            // A call to a @PolymorphicSignature method of MethodHandle/VarHandle resolves to a
            // native method with the call site's signature, which Soot adds to the class
            // (PolymorphicMethodRef). Body construction resolves these calls too, so they are
            // resolved here as well: otherwise the methods are appended to the class in thread
            // order, and the class's Jimple/Shimple lists them in a different order every run.
            // the dummy class is created on first use, as body construction does
            SootClass owner = ownerName.equals(SootClass.INVOKEDYNAMIC_DUMMY_CLASS_NAME)
                ? scene.getSootClass(ownerName) : scene.getSootClassUnsafe(ownerName, false);
            if (owner == null) {
                failed++;
                continue;
            }
            boolean isStatic = Character.isUpperCase(p[0].charAt(0));
            try {
                if (p[0].equalsIgnoreCase("F")) {
                    Type t = AsmUtil.toJimpleType(p[3], noModule);
                    scene.makeFieldRef(owner, p[2], t, isStatic).resolve();
                } else {
                    List<Type> sig = AsmUtil.toJimpleDesc(p[3], noModule);
                    Type ret = sig.remove(sig.size() - 1);
                    scene.makeMethodRef(owner, p[2], sig, ret, isStatic).resolve();
                }
            } catch (RuntimeException ex) {
                // body construction meets the same failure and reports it per method
                failed++;
            }
        }
        return new int[] { refs.size(), failed };
    }

    /** Internal name of Soot's class for invokedynamic call sites. */
    private static final String DUMMY = SootClass.INVOKEDYNAMIC_DUMMY_CLASS_NAME.replace('.', '/');

    private static final class RefCollector extends ClassVisitor {
        private final TreeSet<String> refs;

        RefCollector(TreeSet<String> refs) {
            super(Opcodes.ASM9);
            this.refs = refs;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0)
                return null;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitFieldInsn(int opcode, String owner, String fname, String fdesc) {
                    if (owner.startsWith("["))
                        return;
                    boolean st = opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC;
                    refs.add((st ? "F" : "f") + "|" + owner + "|" + fname + "|" + fdesc);
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String mname, String mdesc, boolean itf) {
                    if (owner.startsWith("["))
                        return;
                    refs.add((opcode == Opcodes.INVOKESTATIC ? "M" : "m") + "|" + owner + "|" + mname + "|" + mdesc);
                }

                // Soot models a call site as a static method of soot.dummy.InvokeDynamic named
                // after the site, and turns the bootstrap handle and its arguments into refs.
                @Override
                public void visitInvokeDynamicInsn(String iname, String idesc, Handle bsm, Object... bsmArgs) {
                    refs.add("M|" + DUMMY + "|" + iname + "|" + idesc);
                    constant(bsm);
                    for (Object a : bsmArgs)
                        constant(a);
                }

                @Override
                public void visitLdcInsn(Object value) {
                    constant(value);
                }
            };
        }

        /** The refs of a MethodHandle constant, with Soot's static flags (AsmMethodSource.toSootMethodRef/FieldRef). */
        private void constant(Object c) {
            if (c instanceof Handle) {
                Handle h = (Handle) c;
                if (h.getOwner().startsWith("["))
                    return;
                switch (h.getTag()) {
                    case Opcodes.H_GETFIELD: case Opcodes.H_PUTFIELD:
                        refs.add("f|" + h.getOwner() + "|" + h.getName() + "|" + h.getDesc()); break;
                    case Opcodes.H_GETSTATIC: case Opcodes.H_PUTSTATIC:
                        refs.add("F|" + h.getOwner() + "|" + h.getName() + "|" + h.getDesc()); break;
                    default:
                        refs.add((h.getTag() == Opcodes.H_INVOKESTATIC ? "M" : "m") + "|" + h.getOwner() + "|" + h.getName() + "|" + h.getDesc());
                }
            } else if (c instanceof ConstantDynamic) {
                ConstantDynamic d = (ConstantDynamic) c;
                constant(d.getBootstrapMethod());
                for (int i = 0; i < d.getBootstrapMethodArgumentCount(); i++)
                    constant(d.getBootstrapMethodArgument(i));
            }
        }
    }
}
