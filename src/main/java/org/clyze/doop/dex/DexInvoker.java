package org.clyze.doop.dex;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.Database;
import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.common.android.AndroidSupport;
import org.clyze.utils.Helper;
import org.clyze.utils.JHelper;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.MultiDexContainer;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.jf.dexlib2.DexFileFactory.loadDexContainer;

class DexInvoker {

    private static final String APKTOOL_HOME_ENV_VAR = "APKTOOL_HOME";

    public static void main(String[] args) throws DoopErrorCodeException {
        DexParameters dexParams = new DexParameters();
        dexParams.initFromArgs(args);

        String outDir = dexParams.getOutputDir();
        try {
            Helper.initLogging("DEBUG", outDir + File.separator + "logs", true);
        } catch (IOException ex) {
            System.err.println("Warning: could not initialize logging");
            throw new DoopErrorCodeException(15);
        }

        Log logger = LogFactory.getLog(DexInvoker.class);
        logger.debug("Using output directory: " + outDir);

        try (Database db = new Database(new File(outDir))) {
            CHA cha = new CHA();
            DexFactWriter writer = new DexFactWriter(db, cha);
            BasicJavaSupport java = new BasicJavaSupport();
            java.preprocessInputs(dexParams);
            writer.writePreliminaryFacts(java, dexParams);
            AndroidSupport android = new DexAndroidSupport(dexParams, java);
            try {
                Set<String> tmpDirs = new HashSet<>();
                android.processInputs(tmpDirs);
                System.out.println("Writing components...");
                android.writeComponents(writer, dexParams);
                android.printCollectedComponents();
                JHelper.cleanUp(tmpDirs);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            for (String apkName : dexParams.getAllInputs()) {
                if (!apkName.endsWith(".apk")) {
                    System.err.println("Input file is not an .apk file: " + apkName);
                    continue;
                }

                File apk = new File(apkName);
                if (!apk.exists())
                    throw new RuntimeException("APK does not exist: " + apkName);

                decompressApk(apk, dexParams.getDecompressDir());

                try {
                    Opcodes opcodes = Opcodes.getDefault();
                    MultiDexContainer<? extends DexBackedDexFile> multiDex = loadDexContainer(apk, opcodes);
                    long time1 = System.currentTimeMillis();
                    for (String dexEntry : multiDex.getDexEntryNames()) {
                        DexBackedDexFile dex = multiDex.getEntry(dexEntry);
                        if (dex != null) {
                            System.out.println("Found dex file '" + dexEntry + "' with " + dex.getClassCount() + " classes in '" + apkName + "'");
                            writer.generateFacts(java, dexParams, apk.getName(), dexEntry, dex);
                        } else
                            throw new RuntimeException("Internal error: null .dex entry for " + dexEntry);
                    }
                    long time2 = System.currentTimeMillis();
                    System.out.println("Dex processing time: " + ((time2 - time1) / 1000.0) + " sec");
                } catch (IOException e) {
                    System.err.println("Error opening APK " + apkName);
                    throw e;
                }
            }

            writer.writeLastFacts(java);
            cha.conclude(db, writer, dexParams.printPhantoms());
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new DoopErrorCodeException(5, ex);
        }
    }

    /**
     * Decompress an APK input using apktool. Needs environment variable set
     * (name found in constant APKTOOL_HOME_ENV_VAR).
     *
     * @param apk                        the APK
     * @param decompressDir              the target directory to use as root
     * @throws DoopErrorCodeException    an exception that Doop must handle
     */
    private static void decompressApk(File apk, String decompressDir) throws DoopErrorCodeException {
        if (decompressDir == null)
            return;

        if (new File(decompressDir).mkdirs())
            System.out.println("Created " + decompressDir);

        String apktoolHome = System.getenv(APKTOOL_HOME_ENV_VAR);
        if (apktoolHome == null) {
            System.err.println("Cannot decompress APK, environment variable missing: " + APKTOOL_HOME_ENV_VAR);
            throw new DoopErrorCodeException(13);
        }
        try {
            String apkPath = apk.getCanonicalPath();
            String outDir = decompressDir + File.separator + apkBaseName(apk.getName());
            // Don't use "-f" option of apktool, delete manually.
            FileUtils.deleteDirectory(new File(outDir));
            String[] cmd = {
                    apktoolHome + File.separator + "apktool",
                    "d", apkPath,
                    "-o", outDir
            };
            System.out.println("Decompressing " + apkPath + " using apktool...");
            System.out.println("Command: " + String.join(" ", cmd));
            JHelper.runWithOutput(cmd, "APKTOOL");
        } catch (IOException ex) {
            System.err.println("Error: could not run apktool (" + APKTOOL_HOME_ENV_VAR + " = " + apktoolHome + ").");
            throw new DoopErrorCodeException(14, ex);
        }
    }

    private static String apkBaseName(String apkName) {
        if (!apkName.endsWith(".apk"))
            throw new RuntimeException("Cannot find base name of " + apkName);
         return apkName.substring(0, apkName.length()-4);
    }
}
