package org.clyze.doop

import org.clyze.doop.core.Doop
import org.clyze.doop.system.*

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
                def dir = FileOps.findDirOrThrow(args[0], "Invalid directory: ${args[0]}")
                def properties = new File(dir, "doop.properties")
                CommandLineAnalysisFactory.createEmptyProperties(properties)

                def checksums = new File(dir, "checksums.properties")
                def sootClassesJar = FileOps.findFileOrThrow(args[1], "Invalid soot classes jar: ${args[1]}")
                def alg = "SHA-256"
                checksums.withWriter { w ->
                    w.write """\
                            #This file is generated automatically. Do not modify.
                            ${Doop.SOOT_CHECKSUM_KEY} = ${CheckSum.checksum(sootClassesJar, alg)}
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
