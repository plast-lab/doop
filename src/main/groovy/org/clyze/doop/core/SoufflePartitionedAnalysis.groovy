package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.transform.TypeChecked
import groovy.util.logging.Log4j
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.clyze.doop.utils.SouffleOptions

import static groovy.io.FileType.FILES
import static org.apache.commons.io.FileUtils.*

@CompileStatic
@InheritConstructors
@Log4j
@TypeChecked
class SoufflePartitionedAnalysis extends SouffleAnalysis {

    @Override
    void run() {
        File analysis = new File(outDir, "${name}.dl")
        deleteQuietly(analysis)
        analysis.createNewFile()

        initDatabase(analysis)
        runAnalysisAndProduceStats(analysis)

        def script = newScriptForAnalysis(executor)

        Future<File> compilationFuture = null
        def executorService = Executors.newSingleThreadExecutor()
        SouffleOptions souffleOpts = new SouffleOptions(options)
        if (!options.FACTS_ONLY.value) {
            compilationFuture = executorService.submit(new Callable<File>() {
                @Override
                File call() {
                    log.info "[Task COMPILE...]"
                    def generatedFile = script.compile(analysis, outDir, souffleOpts)
                    log.info "[Task COMPILE Done]"
                    return generatedFile
                }
            })
        }

        try {
            log.info "[Task FACTS...]"
            generateFacts()
            File destPartitionsFile = new File(factsDir, "TypeToPartition.facts")
            def lines = destPartitionsFile.readLines()

            def partitionSizes = [:] as HashMap<String, Integer>
            lines.each { String line ->
                String[] lineParts = line.split('\t')
                def partition = lineParts[1]
                if (partitionSizes.containsKey(partition)) {
                    def currentSize = partitionSizes.get(partition)
                    partitionSizes.put(partition, currentSize+1)
                }
                else {
                    partitionSizes.put(partition, 0)
                }
            }

	        partitionSizes.each() { partition, size ->
		        log.info "Partition: ${partition} size: ${size}"
	        }

            def partitions = partitionSizes.keySet()
            int partitionNumber = 0
            partitions.each { partition ->
                partitionNumber++
                def childOutDir = new File(outDir.canonicalPath + "-part-" + partitionNumber)
	            if (childOutDir.exists()) {
		            childOutDir.deleteDir()
	            }
                def childFactsDir = new File(childOutDir, "facts")
	            childOutDir.mkdirs()
	            childFactsDir.mkdirs()

                factsDir.eachFileMatch FILES, ~/.*\.facts/, { File factsFile ->
	                def link = new File(childFactsDir, factsFile.name).toPath()
	                Files.createSymbolicLink(link, factsFile.toPath())
                }

                new File(childFactsDir, "PrimaryPartition.facts").withWriter{ w ->
                    w.writeLine(partition)}
            }

            log.info "[Task FACTS Done]"

            if (options.FACTS_ONLY.value) return

            def generatedFile = compilationFuture.get()
            database.mkdirs()

            def analysisExecutorService = Executors.newFixedThreadPool(partitions.size())
            File runtimeMetricsFile = new File(database, "Stats_Runtime.csv")
            File statsMetricsFile = new File(database, "Stats_Metrics.csv")
            runtimeMetricsFile.createNewFile()
            statsMetricsFile.createNewFile()

            partitionNumber = 0
            partitions.each { partition ->
                partitionNumber++
                def childOutDir = new File(outDir.canonicalPath + "-part-" + partitionNumber)
                def childFactsDir = new File(childOutDir, "facts")

                analysisExecutorService.submit(new Runnable() {
                    @Override
                    void run() {
                        def childScript = newScriptForAnalysis(executor)
                        childScript.run(generatedFile, childFactsDir, childOutDir,
                                        (options.X_MONITORING_INTERVAL.value as long) * 1000, monitorClosure, souffleOpts)
                        runtimeMetricsFile.append("analysis execution time (sec)\t${childScript.executionTime}\n")
                    }
                })
            }
            analysisExecutorService.shutdown()
            analysisExecutorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)

            partitionNumber = 0

            def insVPTSet = [] as HashSet
            def insReachableSet = [] as HashSet
	        def insCallGraphEdgeSet = [] as HashSet
	        def mayFailCastSet = [] as HashSet
	        def polymorphicCallSiteSet = [] as HashSet
	        def insVPTAppSet = [] as HashSet
	        def insReachableAppSet = [] as HashSet
	        def mayFailCastAppSet = [] as HashSet
	        def polymorphicCallSiteAppSet = [] as HashSet

            partitions.each { partition ->
                partitionNumber++
                def childDatabaseDir = new File(outDir.canonicalPath + "-part-" + partitionNumber + File.separator + "database")
	            def line
	            BufferedReader reader

                def file = new File(childDatabaseDir, "VarPointsTo.csv")
                reader = file.newReader()
	            while ((line = reader.readLine()) != null) {
                    insVPTSet.add(line)
                }

	            file = new File(childDatabaseDir, "Reachable.csv")
	            reader = file.newReader()

	            while ((line = reader.readLine()) != null) {
		            insReachableSet.add(line)
	            }

	            file = new File(childDatabaseDir, "CallGraphEdge.csv")
	            reader = file.newReader()

	            while ((line = reader.readLine()) != null) {
		            insCallGraphEdgeSet.add(line)
	            }

	            file = new File(childDatabaseDir, "MayFailCast.csv")
	            reader = file.newReader()

	            while ((line = reader.readLine()) != null) {
		            mayFailCastSet.add(line)
	            }

	            file = new File(childDatabaseDir, "PolymorphicCallSite.csv")
	            reader = file.newReader()

	            while ((line = reader.readLine()) != null) {
		            polymorphicCallSiteSet.add(line)
	            }

	            file = new File(childDatabaseDir, "VarPointsToApp.csv")
	            reader = file.newReader()
	            while ((line = reader.readLine()) != null) {
		            insVPTAppSet.add(line)
	            }

	            file = new File(childDatabaseDir, "ReachableApp.csv")
	            reader = file.newReader()

	            while ((line = reader.readLine()) != null) {
		            insReachableAppSet.add(line)
	            }

	            file = new File(childDatabaseDir, "MayFailCastApp.csv")
	            reader = file.newReader()

	            while ((line = reader.readLine()) != null) {
		            mayFailCastAppSet.add(line)
	            }

	            file = new File(childDatabaseDir, "PolymorphicCallSiteApp.csv")
	            reader = file.newReader()

	            while ((line = reader.readLine()) != null) {
		            polymorphicCallSiteAppSet.add(line)
	            }
            }

            statsMetricsFile.append("1.0\tvar points-to (INS)\t${insVPTSet.size()}\n")
	        statsMetricsFile.append("8.0\tcall graph edges (INS)\t${insCallGraphEdgeSet.size()}\n")
	        statsMetricsFile.append("11.0\treachable methods (INS)\t${insReachableSet.size()}\n")
	        statsMetricsFile.append("22.0\treachable casts that may fail\t${mayFailCastSet.size()}\n")
	        statsMetricsFile.append("14.0\tpolymorphic call sites\t${polymorphicCallSiteSet.size()}\n")
	        statsMetricsFile.append("5.0\tapp var points-to (INS)\t${insVPTAppSet.size()}\n")
	        statsMetricsFile.append("25.5\tapp reachable methods (INS)\t${insReachableAppSet.size()}\n")
	        statsMetricsFile.append("25.0\tapp reachable casts that may fail\t${mayFailCastAppSet.size()}\n")
	        statsMetricsFile.append("18.0\tapp polymorphic call sites\t${polymorphicCallSiteAppSet.size()}\n")

            int dbSize = (sizeOfDirectory(database) / 1024).intValue()

            runtimeMetricsFile.append("analysis compilation time (sec)\t${script.compilationTime}\n")
            runtimeMetricsFile.append("disk footprint (KB)\t$dbSize\n")
            runtimeMetricsFile.append("fact generation time (sec)\t$factGenTime\n")
        } finally {
            executorService.shutdownNow()
        }
    }
}
