package org.clyze.doop.dex;

import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.Database;
import org.clyze.doop.util.TypeUtils;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;

import java.util.Map;

class DexThreadFactory {
    private final Database db;
    private final BasicJavaSupport java;
    private final DexParameters dexParams;
    private final Map<String, MethodSig> cachedMethodDescriptors;
    private final CHA cha;
    private final String dexEntry;
    private final String apkName;

    /**
     * Generates a thread factory for the parallel fact generation of a single
     * .dex entry in an .apk. A new factory of this type should be generated for
     * each .dex entry.
     *
     * @param db                        the database object
     * @param dexParams                 the front-end parameters
     * @param dexEntry                  the .dex entry ("classes.dex")
     * @param apkName                   the .apk name ("app.apk")
     * @param java                      the Java support object
     * @param cachedMethodDescriptors   the cache of method descriptors
     * @param cha                       the global CHA object
     */
    DexThreadFactory(Database db, DexParameters dexParams,
                            String dexEntry, String apkName, CHA cha,
                            BasicJavaSupport java, Map<String, MethodSig> cachedMethodDescriptors) {
        this.db = db;
        this.dexParams = dexParams;
        this.dexEntry = dexEntry;
        this.apkName = apkName;
        this.java = java;
        this.cachedMethodDescriptors = cachedMethodDescriptors;
        this.cha = cha;
    }

    public Runnable newFactGenRunnable(Iterable<DexBackedClassDef> classes) {
        return () -> {
            for (DexBackedClassDef dexClass : classes) {
                String className = TypeUtils.raiseTypeId(dexClass.getType());
                java.registerArtifactClass(apkName, className, dexEntry);
                DexClassFactWriter classWriter = new DexClassFactWriter(db);
                classWriter.generateFacts(dexClass, className, dexParams, cachedMethodDescriptors);
                cha.registerDefinedMethods(classWriter.definedMethods);
                cha.queueFieldOps(classWriter.fieldOps);
                cha.registerSuperClass(className, classWriter.superClass);
                cha.registerDefinedClassFields(className, classWriter.definedFields);
            }
        };
    }
}
