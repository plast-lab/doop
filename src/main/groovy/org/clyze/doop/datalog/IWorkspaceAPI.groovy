package org.clyze.doop.datalog

interface IWorkspaceAPI {
    IWorkspaceAPI echo(String message)
    IWorkspaceAPI startTimer()
    IWorkspaceAPI elapsedTime()
    IWorkspaceAPI transaction()
    IWorkspaceAPI timedTransaction(String message)
    IWorkspaceAPI commit()
    IWorkspaceAPI createDB(String database)
    IWorkspaceAPI openDB(String database)
    IWorkspaceAPI addBlock(String logiqlString)
    IWorkspaceAPI addBlockFile(String filePath)
    IWorkspaceAPI addBlockFile(String filePath, String blockName)
    IWorkspaceAPI execute(String logiqlString)
    IWorkspaceAPI executeFile(String filePath)

    IWorkspaceAPI eval(String cmd)
    IWorkspaceAPI external(String cmd)

    IWorkspaceAPI include(String filePath)
}

