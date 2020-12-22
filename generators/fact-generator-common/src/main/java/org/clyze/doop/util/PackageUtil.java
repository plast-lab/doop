package org.clyze.doop.util;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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

	/**
	 * Returns a set of the packages contained in the given jar.
	 * Any classes that are not included in packages are also retrieved.
	 */
	public static Set<String> getPackages(File archive) throws IOException {
		String name = archive.getName().toLowerCase();
		if (name.endsWith(".jar") || name.endsWith(".zip"))
			return getPackagesForJAR(archive);
		else if (name.endsWith(".apk"))
			return getPackagesForAPK(archive);
		else if (name.endsWith(".aar"))
			return getPackagesForAAR(archive);
		else if (name.endsWith(".class"))
			return getPackagesForBytecode(archive);
		System.err.println("Cannot compute packages, unknown file format: " + archive);
		return new HashSet<>();
	}

	public static Set<String> getPackagesForBytecode(File f) throws IOException {
		return new HashSet<>(Collections.singletonList(getPackageFromDots(BytecodeUtil.getClassName(f))));
	}

	public static Set<String> getPackagesForAPK(File apk) throws IOException {
		Set<String> pkgs = new HashSet<>();
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

	public static String getPackageFromSlashes(String s) {
		int idx = s.lastIndexOf('/');
		s = s.replaceAll("/", ".");
		return idx == -1 ? s : s.substring(0, idx) + ".*";
	}

	public static String getPackageFromDots(String s) {
		int idx = s.lastIndexOf('.');
		return idx == -1 ? s : s.substring(0, idx) + ".*";
	}

	public static Set<String> getPackagesForAAR(File aar) throws IOException {
		Set<String> ret = new HashSet<>();
		Set<String> tmpDirs = new HashSet<>();
		for (String jar : ContainerUtils.toJars(Collections.singletonList(aar.getCanonicalPath()), true, tmpDirs))
			ret.addAll(getPackagesForJAR(new File(jar)));
		JHelper.cleanUp(tmpDirs);
		return ret;
	}

	static String getPackageFromClassName(String className) {
		if (className.indexOf("/") > 0)
			return FilenameUtils.getPath(className).replace('/', '.') + "*";
		else
			return FilenameUtils.getBaseName(className);
	}

	static Set<String> getPackagesForJAR(File jar) throws IOException {
		ZipFile zip = new ZipFile(jar);
		Enumeration<? extends ZipEntry> entries = zip.entries();
		Set<String> packages = new HashSet<>();
		while (entries.hasMoreElements()) {
			final ZipEntry ze = entries.nextElement();
			if (ze.getName().endsWith(".class")) {
				packages.add(getPackageFromClassName(ze.getName()));
			}
		}
		return packages;
	}
}
