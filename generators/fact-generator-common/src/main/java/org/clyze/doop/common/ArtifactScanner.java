package org.clyze.doop.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.utils.TypeUtils;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.ClassNode;

import static org.jf.dexlib2.DexFileFactory.loadDexContainer;
import static org.objectweb.asm.Opcodes.*;

/**
 * This class scans input artifacts (.jar, .aar, or .apk files) and
 * registers each found class. Optional actions can also be performed
 * on the artifact entries, when they are scanned.
 */
public class ArtifactScanner {

    private final Map<String, Set<ArtifactEntry>> artifactToClassMap = new ConcurrentHashMap<>();
    private final Log logger = LogFactory.getLog(getClass());
    private Set<GenericFieldInfo> genericFields = new HashSet<>();

    Set<GenericFieldInfo> getGenericFields() { return genericFields; }

    public Map<String, Set<ArtifactEntry>> getArtifactToClassMap() {
        return artifactToClassMap;
    }

    /**
     * Registers a class with its container artifact.
     * @param artifact     the file name of the artifact containing the class
     * @param className    the name of the class
     * @param subArtifact  the sub-artifact (such as "classes.dex" for APKs)
     * @param size         the size of the class
     */
    public void registerArtifactClass(String artifact, String className, String subArtifact, int size) {
        ArtifactEntry ae = new ArtifactEntry(className, subArtifact, size);
        artifactToClassMap.computeIfAbsent(artifact, x -> new CopyOnWriteArraySet<>()).add(ae);
    }

    // // Soot-based method.
    // public void processAPKClasses(String inputApk, Consumer<String> classProc) {
    //     File apk = new File(inputApk);
    //     String artifact = apk.getName();
    //     try {
    //         List<DexContainer> listContainers = DexFileProvider.v().getDexFromSource(apk);
    //         int dexClassesCount = 0;
    //         for (DexContainer dexContainer : listContainers) {
    //             Set<? extends DexBackedClassDef> dexClasses = dexContainer.getBase().getClasses();
    //             dexClassesCount += dexClasses.size();
    //             for (DexBackedClassDef dexBackedClassDef : dexClasses) {
    //                 String className = TypeUtils.raiseTypeId(dexBackedClassDef.getType());
    //                 classProc.accept(className);
    //                 registerArtifactClass(artifact, className, dexContainer.getDexName(), dexBackedClassDef.getSize());
    //             }
    //         }
    //         System.out.println("Classes found in apk: " + dexClassesCount);
    //     } catch (IOException ex) {
    //         System.err.println("Could not read dex classes in " + apk);
    //         ex.printStackTrace();
    //     }
    // }

    /**
     * Register .dex entries and perform actions over .dex entries (if
     * processor is not null).
     *
     * @param inputApk    the path of the input APK file
     * @param classProc   the processor for .class entries (takes entry name)
     */
    public void processAPKClasses(String inputApk, Consumer<String> classProc) {
        try {
            Opcodes opcodes = Opcodes.getDefault();
            File apk = new File(inputApk);
            MultiDexContainer<? extends DexBackedDexFile> multiDex = loadDexContainer(apk, opcodes);
            for (String dexEntry : multiDex.getDexEntryNames()) {
                DexBackedDexFile dex = multiDex.getEntry(dexEntry);
                if (dex == null)
                    logger.debug("No .dex entry for " + dexEntry);
                else
                    for (DexBackedClassDef dexClass : dex.getClasses()) {
                        String className = TypeUtils.raiseTypeId(dexClass.getType());
                        registerArtifactClass(apk.getName(), className, dexEntry, dexClass.getSize());
                        if (classProc != null)
                            classProc.accept(className);
                    }
            }
        } catch (Exception ex) {
            logger.debug("Error while calculating artifacts on Android: " + ex.getMessage());
        }
    }

    public void processClass(InputStream is, File f, Consumer<String> classProc) throws IOException {
        ClassReader reader = new ClassReader(is);
        String className = BytecodeUtil.getClassName(reader);
        String artifact = f.getName();
        registerArtifactClass(artifact, className, "-", reader.b.length);
        if (classProc != null)
            classProc.accept(className);

//        ClassNode cn = new ClassNode(ASM5);
//        reader.accept(cn, ClassReader.EXPAND_FRAMES);
//        ClassVisitor genericSignaturesRetriever = new GenericSignaturesRetriever(ASM5);
//        cn.accept(genericSignaturesRetriever);
//        Set<GenericFieldInfo> classGenericFields = ((GenericSignaturesRetriever) genericSignaturesRetriever).getGenericFields();
//        this.genericFields.addAll(classGenericFields);
    }

    /**
     * Register archive (.class) entries and perform actions over
     * other types of entries (if processors are not null).
     *
     * @param input       the path of the input archive
     * @param classProc   the processor for .class entries (takes entry name)
     * @param generalProc the general processor for all other entries
     */
    public void processArchive(String input, Consumer<String> classProc,
                               EntryProcessor generalProc) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(input)); ZipFile zipFile = new ZipFile(input)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                /* Skip directories */
                if (entry.isDirectory())
                    continue;

                String entryName = entry.getName().toLowerCase();
                if (entryName.endsWith(".class")) {
                    try {
                        processClass(zipFile.getInputStream(entry), new File(zipFile.getName()), classProc);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        System.err.println(ex.toString());
                        System.err.println("Error while preprocessing entry \"" + entryName + "\", it will be ignored.");
                    }
                } else if (generalProc != null)
                    generalProc.accept(zipFile, entry, entryName);
            }
        }
    }

    /**
     * Helper method to generate the "class-artifact" table for a list of inputs.
     *
     * @param inputs   a list of inputs (such as JAR or APK files)
     */
    public void onlyRegisterArtifactClasses(Iterable<String> inputs) throws IOException {
        for (String input : inputs) {
            String lower = input.toLowerCase();
            if (lower.endsWith(".jar") || lower.endsWith(".zip"))
                processArchive(input, null, null);
            else if (lower.endsWith(".apk"))
                processAPKClasses(input, null);
            else if (lower.endsWith(".aar"))
                System.err.println("AAR input not supported for class-artifact table: " + input);
        }
    }

    /**
     * Helper method to extract an entry inside a ZIP archive and save
     * it as a file.
     *
     * @param tmpDirName   a name for the intermediate temporary directory
     * @param zipFile      the ZIP archive
     * @param entry        the archive entry
     * @param entryName    the name of the entry
     * @return             the output file
     */
    public static File extractZipEntryAsFile(String tmpDirName, ZipFile zipFile, ZipEntry entry, String entryName) throws IOException {
        File tmpDir = Files.createTempDirectory(tmpDirName).toFile();
        tmpDir.deleteOnExit();
        String tmpName = entryName.replaceAll(File.separator, "_");
        File libTmpFile = new File(tmpDir, tmpName);
        libTmpFile.deleteOnExit();
        Files.copy(zipFile.getInputStream(entry), libTmpFile.toPath());
        return libTmpFile;
    }

    @FunctionalInterface
    public interface EntryProcessor {
        /**
         * Process an entry inside a .zip (JAR/AAR/APK) file.
         *
         * @param file       the ZIP file
         * @param entry      the ZIP entry
         * @param entryName  the name of the ZIP entry
         */
        void accept(ZipFile file, ZipEntry entry, String entryName) throws IOException;
    }
}
