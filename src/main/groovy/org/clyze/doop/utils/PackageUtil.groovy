package org.clyze.doop.utils

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.apache.commons.io.FilenameUtils
import org.clyze.utils.AARUtils
import org.clyze.utils.Helper
import org.jf.dexlib2.dexbacked.DexBackedClassDef
import org.jf.dexlib2.iface.MultiDexContainer
import static org.jf.dexlib2.DexFileFactory.loadDexContainer

/** Computes the app-regex for JAR/APK/AAR inputs. */
class PackageUtil {

	/**
	 * Returns a set of the packages contained in the given jar.
	 * Any classes that are not included in packages are also retrieved.
	 */
	static Set<String> getPackages(File archive) {
		String name = archive.getName().toLowerCase()
		if (name.endsWith(".jar") || name.endsWith(".zip")) {
			return getPackagesForJAR(archive)
		} else if (name.endsWith(".apk")) {
			return getPackagesForAPK(archive)
		} else if (name.endsWith(".aar")) {
			return getPackagesForAAR(archive)
		}
		System.err.println "Cannot compute packages, unknown file format: ${name}"
		return [] as Set
	}

	static Set<String> getPackagesForAPK(File apk) {
		Set<String> pkgs = []
		MultiDexContainer multiDex = loadDexContainer(apk, null)
		for (String dex : multiDex.getDexEntryNames()) {
			def dexFile = multiDex.getEntry(dex)
			for (DexBackedClassDef dexClass : dexFile.getClasses()) {
				String className = dexClass.toString()
				if (!className.startsWith("L") || !className.endsWith(";")) {
					System.err.println("getPackagesForAPK: bad class " + className)
				} else {
					pkgs << getPackageFromSlashes(className[1..className.size()-1])
				}
			}
		}
		return pkgs
	}

	static String getPackageFromSlashes(String s) {
		def idx = s.lastIndexOf('/')
		String ret = idx == -1 ? s : s.substring(0, idx)
		return ret.replaceAll("/", ".") + '.*'
	}

	static String getPackageFromDots(String s) {
		def idx = s.lastIndexOf('.')
		return idx == -1 ? s : s.substring(0, idx) + '.*'
	}

	static Set<String> getPackagesForAAR(File aar) {
		Set<String> ret = [] as Set
		Set<String> tmpDirs = [] as Set
		List<String> jars = AARUtils.toJars([aar.canonicalPath], true, tmpDirs)
		jars.each { jar -> ret.addAll(getPackagesForJAR(new File(jar))) }
		Helper.cleanUp(tmpDirs)
		return ret
	}

	static Set<String> getPackagesForJAR(File jar) {
		def zip = new ZipFile(jar)
		Enumeration<? extends ZipEntry> entries = zip.entries()
		List<ZipEntry> classes = entries?.findAll { it.name.endsWith(".class") }
		List<String> packages = classes.collect { ZipEntry entry ->
			def className = entry.name
			if (className.indexOf("/") > 0)
				return FilenameUtils.getPath(className).replace('/' as char, '.' as char) + '*'
			else
				return FilenameUtils.getBaseName(className)
		}
		return packages.toSet()
	}
}
