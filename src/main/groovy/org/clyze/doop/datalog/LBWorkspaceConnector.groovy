package org.clyze.doop.datalog

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.doop.system.*

class LBWorkspaceConnector implements IWorkspaceAPI {

    LBCommandQueue _queue

    Log              _logger
    File             _outDir
    String           _bloxbatch
    String           _bloxOpts
    Executor         _executor

    String           _workspace

    LBWorkspaceConnector(File outDir, String bloxbatch, String bloxOpts, Executor executor, CPreprocessor cpp) {
        _queue = new LBCommandQueue(outDir, cpp)
        _logger = LogFactory.getLog(getClass())
        _outDir = outDir
        _bloxbatch = bloxbatch
        _bloxOpts = bloxOpts
        _executor = executor
    }

    public IWorkspaceAPI connect(String workspace) {
        _workspace = workspace
        return this
    }
    public IWorkspaceAPI disconnect() {
        _workspace = null
        return this
    }

    LBCommandQueue queue() {
        return _queue
    }

    void processQueue() {
        _queue._components.each { c -> c.invoke(_logger, _bloxbatch, _bloxOpts, _executor) }
        _queue.clear()
    }

    public IWorkspaceAPI echo(String message) {
        throw new UnsupportedOperationException("echo")
    }
    public IWorkspaceAPI startTimer() {
        throw new UnsupportedOperationException("startTimer")
    }
    public IWorkspaceAPI elapsedTime() {
        throw new UnsupportedOperationException("elapsedTime")
    }
    public IWorkspaceAPI transaction() {
        throw new UnsupportedOperationException("transaction")
    }
    public IWorkspaceAPI commit() {
        throw new UnsupportedOperationException("commit")
    }
    public IWorkspaceAPI createDB(String database) {
        connect(database)
        return eval("-create -overwrite -blocks base")
    }
    public IWorkspaceAPI openDB(String database) {
        throw new UnsupportedOperationException("openDB")
    }
    public IWorkspaceAPI addBlock(String logiqlString) {
        return eval("-addBlock '$logiqlString'")
    }
    public IWorkspaceAPI addBlockFile(String filePath) {
        return eval("-addBlock -file $filePath")
    }
    public IWorkspaceAPI addBlockFile(String filePath, String blockName) {
        return eval("-addBlock -file $filePath -name $blockName")
    }
    public IWorkspaceAPI execute(String logiqlString) {
        return eval("-execute '$logiqlString'")
    }
    public IWorkspaceAPI executeFile(String filePath) {
        return eval("-execute -file $filePath")
    }

    public IWorkspaceAPI eval(String cmd) {
        exec("$_bloxbatch -db $_workspace $cmd $_bloxOpts")
        return this
    }

    public IWorkspaceAPI external(String cmd) {
        // TODO
        throw new UnsupportedOperationException("external")
    }

    public IWorkspaceAPI include(String filePath) {
        throw new UnsupportedOperationException("include")
    }


    public void processQuery(String logiqlString, String printOpt, Closure outputLineProcessor) {
        exec("$_bloxbatch -db $_workspace -query '$logiqlString' $printOpt", outputLineProcessor)
    }
    public void processQuery(String logiqlString, Closure outputLineProcessor) {
        processQuery(logiqlString, "", outputLineProcessor)
    }
    public void processPredicate(String predicate, Closure outputLineProcessor) {
        processQuery(predicate, "", outputLineProcessor)
    }
    public Map<String, Integer> popCount(String... predicates) {
        def counters = [:]
        exec("$_bloxbatch -db $_workspace -popCount ${predicates.join(',')}") { String line ->
            def num = line.tokenize(':').last()
            def predicate = line[0 .. -( 2 + num.size() )]
            counters[predicate] = num as int
        }

        return counters.asImmutable()
    }
    public List<String> listPredicates() {
        def predicates = []
        exec("$_bloxbatch -db $_workspace -list") { String line -> predicates.add(line) }

        return predicates.asImmutable()
    }

    private void exec(String cmd, Closure closure = Executor.STDOUT_PRINTER) {
        if (_workspace == null) throw new RuntimeException("Not connected to a valid workspace")
        _executor.execute(_outDir.toString(), cmd, closure)
    }


    // Auxialiary classes so the two kinds of components have a common supertype
    static interface IComponent {
        void add(String cmd)
        void invoke(Log logger, String bloxbatch, String bloxOpts, Executor executor)
    }
    static class Script implements IComponent {
        File        _outDir
        File        _script
        PrintWriter _writer

        Script(File outDir) {
            _outDir = outDir
            _script = File.createTempFile("run", ".lb", _outDir)
            _writer = _script.newPrintWriter()
        }
        void add(String cmd) {
            _writer.write(cmd + "\n")
        }
        void invoke(Log logger, String bloxbatch, String bloxOpts, Executor executor) {
            _writer.close()
            logger.info "Using generated script ${_script.getPath()}"
            executor.execute(_outDir.toString(), "$bloxbatch -script ${_script.getPath()} $bloxOpts")
        }
    }
    static class External implements IComponent {
        String _cmd
        void add(String cmd) {
            if (cmd != null) throw new RuntimeException("External Component can only have one command")
            _cmd = cmd
        }
        void invoke(Log logger, String bloxbatch, String bloxOpts, Executor executor) {
            executor.execute(_outDir.toString(), _cmd)
        }
    }
}

