package org.clyze.doop

import org.clyze.doop.system.FileOps

/**
 * A helper class for generating the doop skeleton properties file and the checksums file.
 */
class FileGenerator {

    /**
     * Accepts a single argument, the directory to write files to.
     */
    static void main(String[] args) {
        if (args && args.length == 1) {
            try {
                def dir = FileOps.findDirOrThrow(args[0], "Invalid directory: ${args[0]}")
                def properties = new File(dir, "doop.properties")
                CommandLineAnalysisFactory.createEmptyProperties(properties)
                /*
                //Don't pre-calculate any checksums
                def checksums = new File(dir, "checksums.properties")
                def sootClassesJar = FileOps.findFileOrThrow(args[1], "Invalid soot classes jar: ${args[1]}")
                def alg = "SHA-256"
                checksums.withWriter { w ->
                    w.write """\
                            #This file is generated automatically. Do not modify.
                            ${Doop.SOOT_CHECKSUM_KEY} = ${CheckSum.checksum(sootClassesJar, alg)}
                            """.stripIndent()
                }
                */
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
