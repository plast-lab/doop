package org.clyze.doop.utils

import groovy.transform.CompileStatic
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.apache.commons.io.FilenameUtils
import org.clyze.doop.common.BytecodeUtil
import org.clyze.utils.AARUtils
import org.clyze.utils.JHelper
import org.jf.dexlib2.dexbacked.DexBackedClassDef
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.MultiDexContainer

import static org.jf.dexlib2.DexFileFactory.loadDexContainer

/** Computes the app-regex for JAR/APK/AAR inputs. */
@CompileStatic
class PackageUtil {

	/**
	 * Returns a set of the packages contained in the given jar.
	 * Any classes that are not included in packages are also retrieved.
	 */
	static Set<String> getPackages(File archive) {
		String name = archive.name.toLowerCase()
		if (name.endsWith(".jar") || name.endsWith(".zip")) {
			return getPackagesForJAR(archive)
		} else if (name.endsWith(".apk")) {
			return getPackagesForAPK(archive)
		} else if (name.endsWith(".aar")) {
			return getPackagesForAAR(archive)
		} else if (name.endsWith(".class")) {
			return getPackagesForBytecode(archive)
		}
		System.err.println "Cannot compute packages, unknown file format: ${archive}"
		return [] as Set
	}

	static Set<String> getPackagesForBytecode(File f) {
		[ getPackageFromDots(BytecodeUtil.getClassName(f)) ] as Set<String>
	}

	static Set<String> getPackagesForAPK(File apk) {
		Set<String> pkgs = []
		MultiDexContainer multiDex = loadDexContainer(apk, null)
		for (String dex : (multiDex.dexEntryNames)) {
			DexBackedDexFile dexFile = (DexBackedDexFile)multiDex.getEntry(dex).getDexFile()
			for (DexBackedClassDef dexClass : (dexFile.classes as Set<? extends DexBackedClassDef>)) {
				String className = dexClass.toString()
				if (!className.startsWith("L") || !className.endsWith(";")) {
					System.err.println("getPackagesForAPK: bad class " + className)
				} else {
					pkgs << getPackageFromSlashes(className[1..className.size()-2])
				}
			}
		}
		return pkgs
	}

	static String getPackageFromSlashes(String s) {
		def idx = s.lastIndexOf('/')
		s = s.replaceAll("/", ".")
		return idx == -1 ? s : s.substring(0, idx) + '.*'
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
		JHelper.cleanUp(tmpDirs)
		return ret
	}

	static String getPackageFromClassName(String className) {
		if (className.indexOf("/") > 0)
			return FilenameUtils.getPath(className).replace('/' as char, '.' as char) + '*'
		else
			return FilenameUtils.getBaseName(className)
	}

	static Set<String> getPackagesForJAR(File jar) {
		def zip = new ZipFile(jar)
		Enumeration<? extends ZipEntry> entries = zip.entries()
		Collection<ZipEntry> classes = entries?.findAll { ZipEntry ze -> ze.name.endsWith(".class") }
		List<String> packages = classes.collect { ZipEntry entry ->
			getPackageFromClassName(entry.name)
		}
		return packages.toSet()
	}
}
