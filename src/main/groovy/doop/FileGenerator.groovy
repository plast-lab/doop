package doop

import doop.core.Helper


/**
 * A helper class for generating the doop skeleton properties file and the checksums file.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 */
class FileGenerator {

    /**
     * Accepts three arguments: (a) the directory to write files to, (b) the path of soot-classes jar (c) the path of
     * jphantom jar.
     */
    static void main(String[] args) {
        if (args && args.length == 3) {
            try {
                File dir = Helper.checkDirectoryOrThrowException(args[0], "Invalid directory: ${args[0]}")
                File properties = new File(dir, "doop.properties")
                CommandLineAnalysisFactory.createEmptyProperties(properties)

                File checksums = new File(dir, "checksums.properties")
                File sootClassesJar = Helper.checkFileOrThrowException(args[1], "Invalid soot classes jar: ${args[1]}")
                File jphantomJar = Helper.checkFileOrThrowException(args[2], "Invalid jphantom jar: ${args[2]}")
                String alg = "SHA-256"
                checksums.withWriter { w ->
                    w.write """\
                            #This file is generated automatically. Do not modify.
                            ${doop.core.Doop.SOOT_CHECKSUM_KEY} = ${doop.core.Helper.checksum(sootClassesJar, alg)}
                            ${doop.core.Doop.JPHANTOM_CHECKSUM_KEY} = ${doop.core.Helper.checksum(jphantomJar, alg)}
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
