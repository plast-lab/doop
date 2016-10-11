package org.clyze.doop.datalog

import org.clyze.doop.system.Executor

class LBCommandQueue implements IWorkspaceAPI {

    File             _outDir
    Executor         _executor
    List<LBWorkspaceConnector.IComponent> _components

    public LBCommandQueue(File outDir, Executor executor) {
        _outDir = outDir
        _executor = executor
        _components = new ArrayList<>()
        _components.add(new LBWorkspaceConnector.Script(_outDir))
    }

    public IWorkspaceAPI echo(String message) {
        return eval('echo "' + message + '"')
    }
    public IWorkspaceAPI startTimer() {
        return eval("startTimer")
    }
    public IWorkspaceAPI elapsedTime() {
        return eval("elapsedTime")
    }
    public IWorkspaceAPI transaction() {
        return eval("transaction")
    }
    public IWorkspaceAPI commit() {
        return eval("commit")
    }
    public IWorkspaceAPI createDB(String database) {
        return eval("create $database --overwrite --blocks base")
    }
    public IWorkspaceAPI openDB(String database) {
        return eval("open $database")
    }
    public IWorkspaceAPI addBlock(String logiqlString) {
        return eval("addBlock '$logiqlString'")
    }
    public IWorkspaceAPI addBlockFile(String filePath) {
        return eval("addBlock -F $filePath")
    }
    public IWorkspaceAPI addBlockFile(String filePath, String blockName) {
        return eval("addBlock -F $filePath -B $blockName")
    }
    public IWorkspaceAPI execute(String logiqlString) {
        return eval("exec '$logiqlString'")
    }
    public IWorkspaceAPI executeFile(String filePath) {
        return eval("exec -F $filePath")
    }

    public IWorkspaceAPI eval(String cmd) {
        _components.last().add(cmd)
        return this
    }

    public IWorkspaceAPI external(String cmd) {
        _components.add(new LBWorkspaceConnector.External())
        _components.last().add(cmd)

        _components.add(new LBWorkspaceConnector.Script(_outDir))
    }

    public IWorkspaceAPI include(String filePath) {
        def inDir  = new File(filePath).getParentFile()
        def tmpFile = File.createTempFile("tmp", ".lb", _outDir)
        _executor.execute("cpp -P $filePath $tmpFile")
        tmpFile.eachLine { line ->
            def matcher = (line =~ /^(addBlock|exec)[ \t]+-[a-zA-Z][ \t]+(.*\.logic)$/)
            if (matcher.matches()) {
                def inFile  = matcher[0][2]
                def outFile = inFile.replaceAll(File.separator, "-")
                FileUtils.copyFile(new File(inDir, inFile), new File(_outDir, outFile))
            }
            eval(line)
        }
        return this
    }
}
