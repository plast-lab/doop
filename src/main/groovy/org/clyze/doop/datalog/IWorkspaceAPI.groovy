package org.clyze.doop.datalog

interface IWorkspaceAPI {
    public IWorkspaceAPI echo(String message)
    public IWorkspaceAPI startTimer()
    public IWorkspaceAPI elapsedTime()
    public IWorkspaceAPI transaction()
    public IWorkspaceAPI commit()
    public IWorkspaceAPI createDB(String database)
    public IWorkspaceAPI openDB(String database)
    public IWorkspaceAPI addBlock(String logiqlString)
    public IWorkspaceAPI addBlockFile(String filePath)
    public IWorkspaceAPI addBlockFile(String filePath, String blockName)
    public IWorkspaceAPI execute(String logiqlString)
    public IWorkspaceAPI executeFile(String filePath)

    public IWorkspaceAPI eval(String cmd)
    public IWorkspaceAPI external(String cmd)

    public IWorkspaceAPI include(String filePath)
}

