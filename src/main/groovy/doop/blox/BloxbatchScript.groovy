package doop.blox;

import org.apache.commons.io.FileUtils

class BloxbatchScript {

    File script
    Writer writer;

    public BloxbatchScript(File script) {
        this.script = script
        writer = new PrintWriter(script)
    }

    public String getPath() {
        return script.getPath()
    }

    public void close() {
        writer.close()
    }

    public BloxbatchScript echo(String message) {
        return wr('echo "' + message + '"')
    }
    public BloxbatchScript startTimer() {
        return wr("startTimer")
    }
    public BloxbatchScript elapsedTime() {
        return wr("elapsedTime")
    }
    public BloxbatchScript transaction() {
        return wr("transaction")
    }
    public BloxbatchScript commit() {
        return wr("commit")
    }
    public BloxbatchScript createDB(String database) {
        return wr("create $database --overwrite --blocks base")
    }
    public BloxbatchScript addBlock(String logiqlString) {
        return wr("addBlock '$logiqlString'")
    }
    public BloxbatchScript addBlockFile(String filePath) {
        return wr("addBlock -F $filePath")
    }
    public BloxbatchScript addBlockFile(String filePath, String blockName) {
        return wr("addBlock -F $filePath -B $blockName")
    }
    public BloxbatchScript execute(String logiqlString) {
        return wr("exec '$logiqlString'")
    }
    public BloxbatchScript executeFile(String filePath) {
        return wr("exec -F $filePath")
    }
    public BloxbatchScript wr(String message) {
        writer.println(message)
        return this
    }

    public BloxbatchScript include(String filePath) {
        def file    = new File(filePath)
        def inPath  = file.getParentFile()
        def outPath = script.getParentFile()
        file.eachLine { line ->
            def matcher = (line =~ /^(addBlock|exec)[ \t]+-[a-zA-Z][ \t]+(.*\.logic)$/)
            if (matcher.matches()) {
                def inFile  = matcher[0][2]
                def outFile = inFile.replaceAll(File.separator, "-")
                FileUtils.copyFile(new File(inPath, inFile), new File(outPath, outFile))
            }
            writer.println(line)
        }
        return this
    }
}
