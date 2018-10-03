package org.clyze.doop.dex;

import org.clyze.doop.common.*;
import org.clyze.doop.util.TypeUtils;
import org.jf.dexlib2.dexbacked.*;
import org.jf.dexlib2.dexbacked.reference.DexBackedFieldReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedMethodReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedTypeReference;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class DexFactWriter extends JavaFactWriter {

    private final Map<String, MethodSig> cachedMethodDescriptors;
    private final CHA cha;

    DexFactWriter(Database db, CHA cha) {
        super(db);
        this.cha = cha;
        this.cachedMethodDescriptors = new ConcurrentHashMap<>();
    }

    /**
     * Generates facts for a .dex entry.
     * @param java         the Java support object
     * @param dexParams    the front-end parameters
     * @param apkName      the name of the .apk input
     * @param dexEntry     the name of the .dex entry to process
     * @param dex          the data structure representing the .dex entry
     */
    public void generateFacts(BasicJavaSupport java, DexParameters dexParams,
                              String apkName, String dexEntry, DexBackedDexFile dex)
            throws DoopErrorCodeException {
        DexThreadFactory factory = new DexThreadFactory(_db, dexParams, dexEntry, apkName, cha, java, cachedMethodDescriptors);
        int totalClasses = dex.getClassCount();
        DexDriver driver = new DexDriver(factory, totalClasses, dexParams._cores, false);
        driver.generateInParallel(dex.getClasses());

        // Register all field/type/method references found, to find phantoms later.
        for (DexBackedFieldReference fieldRef : dex.getFields())
            cha.registerReferencedField(new FieldInfo(fieldRef));
        for (DexBackedTypeReference typeRef : dex.getTypes())
            cha.registerReferencedType(TypeUtils.raiseTypeId(typeRef.getType()));
        for (DexBackedMethodReference methRef : dex.getMethods())
            cha.registerReferencedMethod(DexRepresentation.methodId(methRef, null));

        if (dex.getMethodHandleCount() > 0)
            System.err.println("Warning: method handles are not yet supported.");
    }

    public void writePhantomTypes(Iterable<String> phantomTypes) {
        for (String s : phantomTypes) {
            System.out.println("Phantom type: " + s);
            writePhantomType(s);
        }
    }

    public void writePhantomMethods(Iterable<String> phantomMethods) {
        for (String m : phantomMethods) {
            System.out.println("Phantom method: " + m);
            writePhantomMethod(m);
        }
    }

}
