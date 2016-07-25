package org.clyze.doop

import org.clyze.doop.core.Helper
import org.clyze.doop.core.Doop


/**
 * A helper class for generating the doop skeleton properties file and the checksums file.
 */
class FileGenerator {

    /**
     * Accepts three arguments: (a) the directory to write files to, (b) the path of soot-classes jar (c) the path of
     * jphantom jar.
     */
    static void main(String[] args) {
        if (args && args.length == 2) {
            try {
                File dir = Helper.checkDirectoryOrThrowException(args[0], "Invalid directory: ${args[0]}")
                File properties = new File(dir, "doop.properties")
                CommandLineAnalysisFactory.createEmptyProperties(properties)

                File checksums = new File(dir, "checksums.properties")
                File sootClassesJar = Helper.checkFileOrThrowException(args[1], "Invalid soot classes jar: ${args[1]}")
                String alg = "SHA-256"
                checksums.withWriter { w ->
                    w.write """\
                            #This file is generated automatically. Do not modify.
                            ${Doop.SOOT_CHECKSUM_KEY} = ${Helper.checksum(sootClassesJar, alg)}
                            """.stripIndent()
                }
            } catch (e) {
                println e.getMessage()
                System.exit(-1)
            }
        }
        else {
            println "Invalid arguments: ${args}"
            System.exit(-1);
        }
    }
}
