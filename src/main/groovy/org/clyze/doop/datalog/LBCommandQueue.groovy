package org.clyze.doop.datalog

import org.clyze.utils.CPreprocessor

class LBCommandQueue implements IWorkspaceAPI {

    File                                  _outDir
    CPreprocessor                         _cpp
    List<LBWorkspaceConnector.IComponent> _components

    LBCommandQueue(File outDir, CPreprocessor cpp) {
        _outDir = outDir
        _cpp = cpp
        _components = new ArrayList<>()
        _components.add(new LBWorkspaceConnector.Script(_outDir))
    }

    void clear() {
        _components = new ArrayList<>()
    }

    IWorkspaceAPI echo(String message) {
        return eval('\necho "' + message + '"')
    }
    IWorkspaceAPI startTimer() {
        return eval("startTimer")
    }
    IWorkspaceAPI elapsedTime() {
        return eval("elapsedTime")
    }
    IWorkspaceAPI transaction() {
        return eval("transaction")
    }
    IWorkspaceAPI timedTransaction(String message) {
        return  echo(message)
               .startTimer()
               .transaction()
    }
    IWorkspaceAPI commit() {
        return eval("commit")
    }
    IWorkspaceAPI createDB(String database) {
        return eval("create $database --overwrite --blocks base")
    }
    IWorkspaceAPI openDB(String database) {
        return eval("open $database")
    }
    IWorkspaceAPI addBlock(String logiqlString) {
        return eval("addBlock '$logiqlString'")
    }
    IWorkspaceAPI addBlockFile(String filePath) {
        return eval("addBlock -F $filePath")
    }
    IWorkspaceAPI addBlockFile(String filePath, String blockName) {
        return eval("addBlock -F $filePath -B $blockName")
    }
    IWorkspaceAPI execute(String logiqlString) {
        return eval("exec '$logiqlString'")
    }
    IWorkspaceAPI executeFile(String filePath) {
        return eval("exec -F $filePath")
    }

    IWorkspaceAPI eval(String cmd) {
        _components.last().add(cmd)
        return this
    }

    IWorkspaceAPI external(String cmd) {
        _components.add(new LBWorkspaceConnector.External())
        _components.last().add(cmd)

        _components.add(new LBWorkspaceConnector.Script(_outDir))
    }

    IWorkspaceAPI include(String filePath) {
        def inDir  = new File(filePath).getParentFile()
        def tmpFile = File.createTempFile("tmp", ".lb", _outDir)
        _cpp.preprocess(tmpFile.toString(), filePath)
        tmpFile.eachLine { line ->
            def matcher = (line =~ /^(addBlock|exec)[ \t]+-[a-zA-Z][ \t]+(.*\.logic)$/)
            if (matcher.matches()) {
                def inFile  = matcher[0][2]
                def outFile = inFile.replaceAll(File.separator, "-")
                _cpp.preprocess(new File(_outDir, outFile).toString(), new File(inDir, inFile).toString())
            }
            eval(line)
        }
        return this
    }
}
