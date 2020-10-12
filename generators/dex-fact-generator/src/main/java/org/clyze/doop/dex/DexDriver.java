package org.clyze.doop.dex;

import java.util.Map;
import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.CHA;
import org.clyze.doop.common.Database;
import org.clyze.doop.common.Driver;
import org.clyze.utils.TypeUtils;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;

class DexDriver extends Driver<DexBackedClassDef> {
    private final Database db;
    private final BasicJavaSupport java;
    private final DexParameters dexParams;
    private final Map<String, MethodSig> cachedMethodDescriptors;
    private final CHA cha;
    private final String dexEntry;
    private final String apkName;
    @SuppressWarnings("FieldCanBeLocal")
    private final boolean moreStrings;

    /**
     * Generates a driver for the parallel fact generation of a single
     * .dex entry in an .apk. A new driver should be generated for
     * each .dex entry.
     *
     * @param totalClasses              the total number of classes
     * @param cores                     the number of CPU cores
     * @param ignoreFactGenErrors       flag to ignore fact generation errors
     * @param db                        the database object
     * @param dexParams                 the front-end parameters
     * @param dexEntry                  the .dex entry ("classes.dex")
     * @param apkName                   the .apk name ("app.apk")
     * @param cha                       the global CHA object
     * @param moreStrings               enable extraction of more strings
     * @param java                      the Java support object
     * @param cachedMethodDescriptors   the cache of method descriptors
     */
    DexDriver(int totalClasses, Integer cores,
              boolean ignoreFactGenErrors, Database db,
              DexParameters dexParams, String dexEntry,
              String apkName, CHA cha,
              boolean moreStrings,
              BasicJavaSupport java, Map<String, MethodSig> cachedMethodDescriptors) {
        super(totalClasses, cores, ignoreFactGenErrors);
        this.db = db;
        this.dexParams = dexParams;
        this.dexEntry = dexEntry;
        this.apkName = apkName;
        this.cha = cha;
        this.moreStrings = moreStrings;
        this.java = java;
        this.cachedMethodDescriptors = cachedMethodDescriptors;
    }

    @Override
    protected Runnable getFactGenRunnable() {
        return () -> {
            for (DexBackedClassDef dexClass : _tmpClassGroup) {
                String className = TypeUtils.raiseTypeId(dexClass.getType());
                java.getArtifactScanner().registerArtifactClass(apkName, className, dexEntry, dexClass.getSize());
                DexClassFactWriter classWriter = new DexClassFactWriter(db, dexParams);
                classWriter.generateFacts(dexClass, className, dexParams, cachedMethodDescriptors);
                cha.registerDefinedMethods(classWriter.definedMethods);
                cha.queueFieldOps(classWriter.fieldOps);
                if (classWriter.superClass != null)
                    cha.registerSuperClass(className, classWriter.superClass);
                cha.registerDefinedClassFields(className, classWriter.definedFields);
            }
        };
    }

    @Override
    protected Runnable getIRGenRunnable() {
        throw new RuntimeException("Parallel IR generation is not supported.");
    }

}
