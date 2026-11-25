package org.clyze.doop.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.clyze.scanner.BinaryAnalysis;
import static org.clyze.scanner.BinaryAnalysis.AnalysisType.BINUTILS;
import static org.clyze.scanner.BinaryAnalysis.AnalysisType.BUILTIN;
import static org.clyze.scanner.BinaryAnalysis.AnalysisType.RADARE;
import org.clyze.scanner.NativeDatabaseConsumer;
import org.clyze.scanner.NativeScanner;
import org.clyze.utils.TypeUtils;
import org.objectweb.asm.ClassReader;

import static com.android.tools.smali.dexlib2.DexFileFactory.loadDexContainer;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;

/**
 * This class scans input artifacts (.jar, .aar, or .apk files) and
 * registers each found class. Optional actions can also be performed
 * on the artifact entries, when they are scanned.
 */
public class ArtifactScanner {

    private final Map<String, Set<ArtifactEntry>> artifactToClassMap = new ConcurrentHashMap<>();
    private final Map<String, String> artifactNames = new ConcurrentHashMap<>();

    /**
     * Decides how facts name each input file. Facts name an input by its file
     * name, not its path, so they do not depend on where the inputs (or Doop's
     * derived copies, such as flattened multi-release jars) are stored. Files
     * with the same name and the same contents share it. Files with the same
     * name but different contents are told apart by a content hash
     * ("name#hash"), with a warning.
     *
     * @param files  all input files (application, dependencies, platform)
     */
    public void nameArtifacts(Collection<String> files) throws IOException {
        Map<String, List<String>> byName = new TreeMap<>();
        for (String f : files)
            byName.computeIfAbsent(new File(f).getName(), k -> new ArrayList<>()).add(f);
        for (Map.Entry<String, List<String>> e : byName.entrySet()) {
            List<String> same = e.getValue();
            if (same.size() < 2)
                continue;
            Map<String, String> hashes = new LinkedHashMap<>();
            for (String f : same)
                hashes.put(f, contentHash(f));
            if (new HashSet<>(hashes.values()).size() == 1)
                continue;
            for (Map.Entry<String, String> h : hashes.entrySet()) {
                String name = e.getKey() + "#" + h.getValue();
                artifactNames.put(h.getKey(), name);
                System.err.println("WARNING: different inputs share the file name " + e.getKey() + ": " + h.getKey() + " is named " + name + " in the facts");
            }
        }
    }

    /** The name of an input file in the facts (see {@link #nameArtifacts}). */
    public String artifactName(String path) {
        return artifactNames.getOrDefault(path, new File(path).getName());
    }

    private static String contentHash(String file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; )
                md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            byte[] d = md.digest();
            for (int i = 0; i < 6; i++)
                sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
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
        String artifact = artifactName(f.getPath());
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
                // Classes under META-INF/versions/<N>/ are never loaded from
                // here: multi-release jars are flattened for the analyzed
                // platform before fact generation (DoopAnalysis), and in other
                // jars these entries are plain data to the JVM.
                if (entryName.endsWith(".class") && entryName.startsWith("meta-inf/versions/"))
                    continue;
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
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("Error while processing archive " + input + ": " + ex.getMessage());
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
