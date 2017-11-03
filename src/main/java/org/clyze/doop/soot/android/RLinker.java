package org.clyze.doop.soot.android;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import org.apache.commons.io.IOUtils;

import org.clyze.utils.AARUtils;

/** A linker of R-class data.
 *
 *  Given a list of AAR files, this linker extracts R.txt from each
 *  file and creates the corresponding R classes that are needed so
 *  that Doop does not report them as phantom classes. This linker does
 *  not mimic the full logic of the aapt tool, it only generates code
 *  that is good enough for linking (in the form of a JAR file
 *  containing all R.java and R*.class files).
 *
 *  This code calls 'javac' and 'jar', so it may fail when these
 *  programs are not in the path.
 */
public class RLinker {

    /** The entry point of the linker. Takes a list of archives
     *  (containing paths of AAR files) and a map of AAR paths to
     *  package names. Returns the path of the generated JAR (or null
     *  if no code generation was done).
     */
    public static String linkRs(List<String> archives, Map<String, String> pkgs,
                                Set<String> tmpDirs) {
        // The basic data that guide code generation: a map from
        // package names to R nested class names, to contents.
        Map<String, Map<String, List<String> > > rs = new HashMap<>();
        final String tmpDir = AARUtils.createTmpDir(tmpDirs);

        for (String ar : archives) {
            if (ar.endsWith(".aar")) {
                try {
                    String rText = getZipEntry(new ZipFile(ar), "R.txt");
                    if (rText != null) {
                        Set<String> lines = new HashSet<>(Arrays.asList(rText.split("\n|\r")));
                        for (String line : lines)
                            if (line.length() != 0)
                                processRLine(ar, line, pkgs, rs);
                    }
                } catch (IOException ex) {
                    System.err.println("Error while reading R.txt: " + ar);
                    System.err.println(ex.getMessage());
                }
            }
        }

        if (rs.isEmpty()) {
            return null;
        } else {
            rs.forEach ((k, v) -> runProcess("javac " + genR(tmpDir, k, v)));
            String jarName = tmpDir + "/doop-autogen-R.jar";
            runProcess("jar cf " + jarName + " -C " + tmpDir + " .");
            return jarName;
        }
    }

    private static void processRLine(String ar, String line, Map<String, String> pkgs,
                                     Map<String, Map<String, List<String> > > rs) {
        final String delim = " ";
        String[] parts = line.split(delim);
        if (parts.length < 2) {
            System.err.println("Error processing R.txt line: " + line);
        } else {
            String pkg = pkgs.get(ar);
            if (pkg == null) {
                System.err.println("Warning: no package: " + ar);
            } else {
                Map<String, List<String>> pkgEntry = rs.getOrDefault(pkg, new HashMap<String, List<String>>());
                String nestedR = parts[1];
                List<String> list = pkgEntry.getOrDefault(nestedR, new ArrayList<>());
                String rName = pkg + "." + "R$" + nestedR;
                String[] newParts = new String[parts.length];
                newParts[0] = parts[0];
                newParts[1] = parts[2];
                newParts[2] = "=";
                for (int i = 3; i < parts.length; i++) {
                    newParts[i] = parts[i];
                }
                list.add("        public static " + String.join(delim, newParts) + ";");
                pkgEntry.put(nestedR, list);
                rs.put(pkg, pkgEntry);
            }
        }
    }

    private static void runProcess(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            int exitVal = p.exitValue();
            if (exitVal != 0) {
                System.out.println(cmd + " exit value = " + exitVal);
            }
        } catch (Exception ex) {
            System.err.println("Error invoking: " + cmd);
            ex.printStackTrace();
        }
    }

    private static String genR(String tmpDir, String pkg,
                               Map<String, List<String>> rData) {
        String subdir = tmpDir + "/" + pkg.replaceAll("\\.", "/");
        new File(subdir).mkdirs();
        String rFile = subdir + "/R.java";
        System.out.println("Generating " + rFile);
        List<String> lines = new ArrayList<>();
        lines.add("package " + pkg + ";\n");
        lines.add("public final class R {");
        rData.forEach ((k, v) -> genNestedR(k, v, lines));
        lines.add("}");

        try {
            Files.write(Paths.get(rFile), lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("Error generating R class for package: " + pkg);
            ex.printStackTrace();
            return null;
        }
        return rFile;
    }

    private static void genNestedR(String nestedName, List<String> data,
                                   List<String> lines) {
        lines.add("    public static final class " + nestedName + "{\n");
        lines.addAll(data);
        lines.add("    }\n");
    }

    private static String getZipEntry(ZipFile zip, String entryName) {
        try {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.getName().equals(entryName)) {
                    InputStream is = zip.getInputStream(e);
                    return IOUtils.toString(is, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException ex) {
            System.err.println("Error reading " + entryName + " from " + zip.getName());
            System.err.println(ex.getMessage());
        }
        return null;
    }
}
