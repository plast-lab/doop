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

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.clyze.scanner.BinaryAnalysis;
import org.clyze.scanner.NativeDatabaseConsumer;
import org.clyze.scanner.NativeScanner;
import org.clyze.utils.TypeUtils;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.objectweb.asm.ClassReader;
//import org.objectweb.asm.ClassVisitor;
//import org.objectweb.asm.tree.ClassNode;

import static org.clyze.scanner.BinaryAnalysis.AnalysisType.*;
import static org.jf.dexlib2.DexFileFactory.loadDexContainer;
//import static org.objectweb.asm.Opcodes.*;

/**
 * This class scans input artifacts (.jar, .aar, or .apk files) and
 * registers each found class. Optional actions can also be performed
 * on the artifact entries, when they are scanned.
 */
public class ArtifactScanner {

    private final Map<String, Set<ArtifactEntry>> artifactToClassMap = new ConcurrentHashMap<>();
    private final Logger logger = Logger.getLogger(getClass());
    private final Set<GenericFieldInfo> genericFields = ConcurrentHashMap.newKeySet();

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
                MultiDexContainer.DexEntry<?> dex = multiDex.getEntry(dexEntry);
                if (dex == null)
                    logger.debug("No .dex entry for " + dexEntry);
                else
                    for (DexBackedClassDef dexClass : ((DexBackedDexFile)dex.getDexFile()).getClasses()) {
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
        String className = BytecodeUtil.getClassName(new ClassReader(is));
        String artifact = f.getName();
        registerArtifactClass(artifact, className, "-", IOUtils.toByteArray(is).length);
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
                        System.err.println("Error while preprocessing entry \"" + entryName + "\", it will be ignored.");
                    }
                } else if (generalProc != null)
                    generalProc.accept(zipFile, entry, entryName);
            }
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

    public static void scanNativeCode(Database db, Parameters parameters,
				      Set<String> methodStrings) {
        NativeDatabaseConsumer dbc = new DatabaseConnector(db);
        BinaryAnalysis.AnalysisType analysisType;
        if (parameters._nativeRadare)
            analysisType = RADARE;
        else if (parameters._nativeBuiltin)
            analysisType = BUILTIN;
        else if (parameters._nativeBinutils)
            analysisType = BINUTILS;
        else {
            analysisType = BUILTIN;
            System.out.println("No binary analysis type given, using default: " + analysisType.name());
        }
        scanNativeInputs(dbc, analysisType, parameters._preciseNativeStrings, methodStrings, parameters.getInputs());
    }

    private static void scanNativeInputs(NativeDatabaseConsumer dbc,
                                         BinaryAnalysis.AnalysisType binAnalysisType,
                                         boolean preciseNativeStrings,
                                         Set<String> methodStrings,
                                         Iterable<String> inputs) {
        final boolean demangle = false;
        final boolean truncateAddresses = true;
        final NativeScanner scanner = new NativeScanner(true, methodStrings);

        EntryProcessor gProc = (file, entry, entryName) -> scanner.scanArchiveEntry(dbc, binAnalysisType, preciseNativeStrings, truncateAddresses, demangle, file, entry, entryName);
        for (String input : inputs) {
            System.out.println("Processing native code in input: " + input);
            try {
                (new ArtifactScanner()).processArchive(input, null, gProc);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
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
