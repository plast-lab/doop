package org.clyze.doop.dex;

import org.clyze.doop.common.*;
import org.clyze.utils.TypeUtils;
import org.jf.dexlib2.dexbacked.*;
import org.jf.dexlib2.dexbacked.reference.DexBackedFieldReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedMethodReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedTypeReference;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class DexFactWriter extends JavaFactWriter {

    private final Map<String, MethodSig> cachedMethodDescriptors;
    private final CHA cha;

    DexFactWriter(Database db, DexParameters params, CHA cha) {
        super(db, params);
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
        int totalClasses = dex.getClasses().size();
        DexDriver driver = new DexDriver(totalClasses, dexParams._cores, false, _db, dexParams, dexEntry, apkName, cha, _extractMoreStrings, java, cachedMethodDescriptors);
        driver.generateInParallel(dex.getClasses());

        // Register all field/type/method references found, to find phantoms later.
        for (DexBackedFieldReference fieldRef : dex.getFieldSection())
            cha.registerReferencedField(new DexFieldInfo(fieldRef));
        for (DexBackedTypeReference typeRef : dex.getTypeReferences())
            cha.registerReferencedType(TypeUtils.raiseTypeId(typeRef.getType()));
        for (DexBackedMethodReference methRef : dex.getMethodSection())
            cha.registerReferencedMethod(MethodFacts.methodId(methRef));

        if (dex.getMethodHandleSection().size() > 0)
            System.err.println("WARNING: Method handles are not yet supported.");
    }

}
