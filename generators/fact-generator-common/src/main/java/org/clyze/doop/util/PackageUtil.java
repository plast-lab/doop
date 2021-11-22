package org.clyze.doop.util;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.apache.commons.io.FilenameUtils;
import org.clyze.doop.common.BytecodeUtil;
import org.clyze.utils.ContainerUtils;
import org.clyze.utils.JHelper;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.MultiDexContainer;

import static org.jf.dexlib2.DexFileFactory.loadDexContainer;

/** Computes the app-regex for JAR/APK/AAR inputs. */
public class PackageUtil {

	/** Debugging flag. */
	private static final boolean debug = false;

	/**
	 * Returns a set of the packages contained in the given archive.
	 * Any classes that are not included in packages are also retrieved.
	 */
	public static Set<String> getPackages(File archive) throws IOException {
		Set<Regex> packages = ConcurrentHashMap.newKeySet();
		String name = archive.getName().toLowerCase();
		if (name.endsWith(".jar") || name.endsWith(".zip"))
			packages = getPackagesForJAR(archive);
		else if (name.endsWith(".apk"))
			packages = getPackagesForAPK(archive);
		else if (name.endsWith(".aar") || name.endsWith(".war"))
			packages = getPackagesForFatArchive(archive);
		else if (name.endsWith(".class"))
			packages = getPackagesForBytecode(archive);
		else
			System.err.println("Cannot compute packages, unknown file format: " + archive);
		return reducePackages(packages);
	}

	private static Set<String> reducePackages(Collection<Regex> packages) {
		Set<String> ret = ConcurrentHashMap.newKeySet();
		Trie<String, Regex> trie = new PatriciaTrie<>();
		// Exact regular expressions are added to the return set, while prefix
		// expressions are put into a trie to be reduced.
		for (Regex r : packages)
			if (r.isWildcard)
				trie.put(r.text, r);
			else
				ret.add(r.toString());
		// Traverse regex entries and mark as 'deleted' the entries that are
		// covered by other prefixes.
		for (Map.Entry<String, Regex> regexEntry : trie.entrySet()) {
			String regexStr = regexEntry.getKey();
			SortedMap<String, Regex> prefixMap = trie.prefixMap(regexStr);
			// If this prefix matches many regex entries, keep only the entry
			// with this prefix (and mark it as a prefix regex).
			if (prefixMap.size() > 1)
				for (Map.Entry<String, Regex> entryToMark : prefixMap.entrySet())
					if (regexStr.equals(entryToMark.getKey()))
						entryToMark.getValue().isPrefix = true;
					else
						entryToMark.getValue().deleted = true;
		}
		// Add regex entries, ignoring 'deleted' ones.
		for (Regex r : trie.values())
			if (!r.deleted)
				ret.add(r.toString());

		if (debug) {
			System.out.println("APP_REGEX: reduced " + packages.size() + " -> " + ret.size() + " entries");
			System.out.println("Original: " + packages);
			System.out.println("Reduced: " + ret);
		}

		return ret;
	}

	private static Set<Regex> getPackagesForBytecode(File f) throws IOException {
		return new HashSet<>(Collections.singletonList(getPackageFromDots(BytecodeUtil.getClassName(f))));
	}

	private static Set<Regex> getPackagesForAPK(File apk) throws IOException {
		Set<Regex> pkgs = ConcurrentHashMap.newKeySet();
		MultiDexContainer<?> multiDex = loadDexContainer(apk, null);
		for (String dex : multiDex.getDexEntryNames()) {
			DexBackedDexFile dexFile = (DexBackedDexFile)multiDex.getEntry(dex).getDexFile();
			Set<? extends DexBackedClassDef> classes = dexFile.getClasses();
			for (DexBackedClassDef dexClass : classes) {
				String className = dexClass.toString();
				if (!className.startsWith("L") || !className.endsWith(";"))
					System.err.println("getPackagesForAPK: bad class " + className);
				else
					pkgs.add(getPackageFromSlashes(className.substring(1, className.length()-2)));
			}
		}
		return pkgs;
	}

	private static Regex getPackageFromSlashes(String s) {
		int idx = s.lastIndexOf('/');
		s = s.replaceAll("/", ".");
		return idx == -1 ? Regex.exact(s) : Regex.wild(s.substring(0, idx));
	}

	private static Regex getPackageFromDots(String s) {
		int idx = s.lastIndexOf('.');
		return idx == -1 ? Regex.exact(s) : Regex.wild(s.substring(0, idx));
	}

	/**
	 * Helper method to process fat archives, i.e. code archives that may contain
	 * multiple .jar files in addition to .class files. Currently supported
	 * formats: AAR, WAR.
	 *
	 * @param ar             the code archive
	 * @return               the package regex set
	 * @throws IOException   on archive processing error
	 */
	private static Set<Regex> getPackagesForFatArchive(File ar) throws IOException {
		Set<Regex> ret = ConcurrentHashMap.newKeySet();
		Set<String> tmpDirs = ConcurrentHashMap.newKeySet();
		Set<String> jarLibs = ConcurrentHashMap.newKeySet();
		for (String jar : ContainerUtils.toJars(Collections.singletonList(ar.getCanonicalPath()), true, jarLibs, tmpDirs))
			ret.addAll(getPackagesForJAR(new File(jar)));
		for (String jarLib : jarLibs)
			ret.addAll(getPackagesForJAR(new File(jarLib)));
		JHelper.cleanUp(tmpDirs);
		return ret;
	}

	private static Regex getPackageFromClassName(String className) {
		if (className.indexOf("/") > 0) {
			String pre = FilenameUtils.getPath(className).replace('/', '.');
			return Regex.wild(pre.endsWith(".") ? pre.substring(0, pre.length() - 1) : pre);
		} else
			return Regex.exact(FilenameUtils.getBaseName(className));
	}

	private static Set<Regex> getPackagesForJAR(File jar) throws IOException {
		ZipFile zip = new ZipFile(jar);
		Enumeration<? extends ZipEntry> entries = zip.entries();
		Set<Regex> packages = ConcurrentHashMap.newKeySet();
		while (entries.hasMoreElements()) {
			final ZipEntry ze = entries.nextElement();
			if (ze.getName().endsWith(".class"))
				packages.add(getPackageFromClassName(ze.getName()));
		}
		return packages;
	}
}

