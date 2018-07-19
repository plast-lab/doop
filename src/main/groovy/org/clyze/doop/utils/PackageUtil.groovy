package org.clyze.doop.utils

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.clyze.utils.AARUtils
import org.clyze.utils.Helper

import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static soot.DexClassProvider.classesOfDex

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
		Set<String> classNames = []
		def zip = new ZipFile(apk)
		zip.entries().each { ZipEntry entry ->
			if (entry.getName().endsWith(".dex")) {
				def tmpDir = Files.createTempDirectory("apk").toString()
				def tmpDex = "${tmpDir}/temp-classes.dex"
				def is = zip.getInputStream(entry)
				Files.copy(is, Paths.get(tmpDex))
				classNames.addAll(classesOfDex(new File(tmpDex)))
				FileUtils.deleteQuietly(new File(tmpDir))
			}
		}
		return classNames.collect { getPackageFromDots(it) }
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
