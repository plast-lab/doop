package org.clyze.doop.dex;

import org.apache.log4j.Logger;
import org.checkerframework.checker.nullness.qual.*;
import org.clyze.doop.common.Database;
import org.clyze.doop.common.FieldInfo;
import org.clyze.doop.common.FieldOp;
import org.clyze.doop.common.JavaFactWriter;
import org.clyze.utils.TypeUtils;
import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.dexbacked.*;
import org.jf.dexlib2.iface.Annotation;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.value.*;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

import static org.clyze.doop.common.FrontEndLogger.*;
import static org.clyze.doop.common.PredicateFile.*;

/**
 * Writes facts for a single class found in a .dex entry.
 */
class DexClassFactWriter extends JavaFactWriter {

    private static final @Nullable Logger logger = Logger.getLogger(DexClassFactWriter.class);

    public final Collection<FieldOp> fieldOps = new LinkedList<>();
    public final Collection<String> definedMethods = new LinkedList<>();
    public final Collection<FieldInfo> definedFields = new LinkedList<>();
    public @Nullable String superClass;

    DexClassFactWriter(Database db, DexParameters params) {
        super(db, params);
    }

    public void generateFacts(DexBackedClassDef dexClass, String className,
                              DexParameters dexParams, Map<String, MethodSig> cachedMethodDescriptors) {
        if (dexParams.isApplicationClass(className))
            _db.add(APP_CLASS, className);

        for (DexBackedMethod dexMethod : dexClass.getMethods()) {
            DexMethodFactWriter mWriter = new DexMethodFactWriter(dexMethod, _db, dexParams, cachedMethodDescriptors);
            mWriter.writeMethod(fieldOps, definedMethods);
        }

        for (DexBackedField dexField : dexClass.getFields())
            writeField(dexField);

        writeClassOrInterfaceType(dexClass, className);

        for (DexBackedAnnotation annotation : dexClass.getAnnotations())
            _db.add(TYPE_ANNOTATION, className, TypeUtils.raiseTypeId(annotation.getType()));
    }

    private void writeClassOrInterfaceType(ClassDef dexClass, String className) {
        boolean isInterface = false;
        for (AccessFlags flag : AccessFlags.getAccessFlagsForClass(dexClass.getAccessFlags()))
            if (flag == AccessFlags.INTERFACE)
                isInterface = true;
            else
                writeClassModifier(className, flag.toString());
        if (isInterface)
            _db.add(INTERFACE_TYPE, className);
        else
            _db.add(CLASS_TYPE, className);

        String dexSuper = dexClass.getSuperclass();
        if (dexSuper != null) {
            this.superClass = TypeUtils.raiseTypeId(dexSuper);
            _db.add(DIRECT_SUPER_CLASS, className, superClass);
        } else
            logError(logger, "ERROR: no super class found for " + className);

        for (String intf : dexClass.getInterfaces())
            _db.add(DIRECT_SUPER_IFACE, className, TypeUtils.raiseTypeId(intf));
    }

    private void writeField(Field fieldRef) {
        FieldInfo fi = new DexFieldInfo(fieldRef);
        String fieldId = fi.getFieldId();
        String fieldType = fi.type;
        _db.add(FIELD_SIGNATURE, fieldId, fi.definingClass, fi.name, fieldType);
        EncodedValue e = fieldRef.getInitialValue();
        if (e != null) {
            InitialValue initialValue = new InitialValue(e);
            String val = initialValue.value;
            if (val != null) {
                _db.add(FIELD_INITIAL_VALUE, fieldId, val);
                if (initialValue.type == InitialValue.IVType.NUMBER) {
                    if (fieldType.equals("int") || fieldType.equals("long"))
                        writeNumConstantRaw(val, fieldType);
                } else if (initialValue.type == InitialValue.IVType.STRING)
                    writeStringConstant(val);
            }
        }

        AccessFlags[] flags = AccessFlags.getAccessFlagsForField(fieldRef.getAccessFlags());
        for (AccessFlags f : flags)
            _db.add(FIELD_MODIFIER, f.toString(), fieldId);

        for (Annotation annotation : fieldRef.getAnnotations())
            _db.add(FIELD_ANNOTATION, fieldId, TypeUtils.raiseTypeId(annotation.getType()));

        definedFields.add(fi);
    }
}
