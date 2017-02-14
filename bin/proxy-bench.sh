#!/bin/bash

# Benchmark suite for dynamic proxies.

DEBUG="-- -logLevel debugDetail@factbus"

OKDIR="${DOOP_BENCHMARKS}/proxies/okhttp"
GDIR="${DOOP_BENCHMARKS}/proxies/guice"
OPENJDK_DIR="${DOOP_BENCHMARKS}/proxies/openjdk"

# Runs Doop for a benchmark. Arguments:
#   $1 - benchmark JAR
#   $2 - extra arguments (e.g. -platform java_6 or the DEBUG switch)
function runDoopFor {
    ID="${BENCH}${PROXY_SWITCH}"
    # No reflection (turns off dynamic proxy support).
    # CMD="./doop -i $1 -a context-insensitive -id ${ID} ${PROXY_SWITCH} --timeout 500 $2 |& tee doop-facts-${ID}.txt"
    # Just reflection.
    # CMD="./doop -i $1 -a context-insensitive --reflection -id ${ID} ${PROXY_SWITCH} --timeout 500 $2 |& tee doop-facts-${ID}.txt"
    # Classic reflection.
    CMD="./doop -i $1 -a context-insensitive --reflection-classic -id ${ID} ${PROXY_SWITCH} --timeout 500 $2 |& tee doop-facts-${ID}.txt"
    # Classic reflection + speculative use-based analysis.
    # CMD="./doop -i $1 -a context-insensitive --reflection-classic --reflection-speculative-use-based-analysis -id ${ID} ${PROXY_SWITCH} $2 |& tee doop-facts-${ID}.txt"
    # More reflection features turned on.
    # CMD="./doop -i $1 -a context-insensitive --reflection-classic --reflection-speculative-use-based-analysis -id ${ID} --reflection-invent-unknown-objects --reflection-refined-objects ${PROXY_SWITCH} $2 |& tee doop-facts-${ID}.txt"
    # Classic reflection in high soundness mode.
    # CMD="./doop -i $1 -a context-insensitive --reflection-classic --reflection-high-soundness-mode -id ${ID} ${PROXY_SWITCH} --timeout 500 $2 |& tee doop-facts-${ID}.txt"
    echo ${CMD}
    eval ${CMD}
}

if [[ "${DOOP_BENCHMARKS}" == "" ]]
then
    echo 'You must set the DOOP_BENCHMARKS directory'.
    exit
fi

if [[ "$1" == "" ]]
then
    echo You must give the benchmark to run.
    exit
else
    BENCH="$1"
fi

for PROXY_SWITCH in "" "--reflection-dynamic-proxies"
do
    case ${BENCH} in
	dummy)
	    # The empty program.
	    runDoopFor "${DOOP_BENCHMARKS}/proxies/dummy/Main.jar" "-platform java_8"
	    ;;
	proxy-test)
	    # The proxy example.
	    runDoopFor "${DOOP_BENCHMARKS}/proxies/proxy-example-code/Main.jar" ""
	    ;;
	challenges-rvtests)
	    # From "Challenges ..." corpus.
	    runDoopFor "${DOOP_BENCHMARKS}/proxies/challenges-corpus/aopTest-0.0.1-SNAPSHOT.jar" "--main creative.fire.aop.proxy.TaskProxy -platform java_8"
	    ;;
	olingo-app)
	    OLINGODIR="${DOOP_BENCHMARKS}/proxies/olingo-odata4"
	    runDoopFor "${OLINGODIR}/olingo-sample-app.jar ${OLINGODIR}/libs" "--main org.apache.olingo.samples.client.OlingoSampleApp"
	    ;;
	okhttp-mockwebserver)
	    # OkHttp benchmark: mockwebserver.
	    runDoopFor "${OKDIR}/mockwebserver.jar" "--reflection-classic -platform java_8"
	    ;;
	terracotta)
	    TDIR="${DOOP_BENCHMARKS}/proxies/terracotta"
	    # runDoopFor "${TDIR}/dso-l2-5.2-SNAPSHOT.jar ${TDIR}/libs" "--main com.tc.cli.CommandLineMain"
	    runDoopFor "${TDIR}/dso-l2-5.2-SNAPSHOT.jar ${TDIR}/libs" "--main com.tc.server.TCServerMain -platform java_8"
	    ;;
	guice-jndi)
	    # Google Guice JNDI test.
	    runDoopFor "${GDIR}/guice-4.1.0-tests.jar ${GDIR}/libs" "--main com.google.inject.example.JndiProviderClient"
	    ;;
	apache-accumulo)
	    # Apache Accumulo.
	    ACCUMULO_DIR="${DOOP_BENCHMARKS}/proxies/accumulo"
	    runDoopFor "${ACCUMULO_DIR}/accumulo-start-1.4.1-SNAPSHOT.jar ${ACCUMULO_DIR}/libs" "--main org.apache.accumulo.start.Main"
	    # runDoopFor "${ACCUMULO_DIR}/accumulo.jar" "--main org.apache.accumulo.start.Main"
	    ;;
	cafebahn)
	    CAFEBAHN_LIB="${DOOP_BENCHMARKS}/proxies/cafebahn"
	    runDoopFor "${CAFEBAHN_LIB}/rgzm.jar ${CAFEBAHN_LIB}/libs" "--main tochterUhr.gui.Digitaluhr"
	    ;;
	cafebahn-svn)
	    CAFEBAHN_LIB="${DOOP_BENCHMARKS}/proxies/cafebahn-svn"
	    runDoopFor "${CAFEBAHN_LIB}/rgzm.jar ${CAFEBAHN_LIB}/libs" "--main tochterUhr.gui.Digitaluhr"
	    ;;
	xmlblaster)
	    XMLBLASTER_LIB="${DOOP_BENCHMARKS}/proxies/xmlblaster"
	    runDoopFor "${XMLBLASTER_LIB}/xmlBlaster.jar ${XMLBLASTER_LIB}/libs" ""
	    ;;
	eclipse-nebula)
	    NEBULA_LIB="${DOOP_BENCHMARKS}/proxies/eclipse-nebula"
	    runDoopFor "${NEBULA_LIB}/org.eclipse.nebula.widgets.calendarcombo-1.0.0-SNAPSHOT.jar ${NEBULA_LIB}/libs" "--main src.org.eclipse.nebula.widgets.calendarcombo.Tester"
	    ;;
	# junit)
	#     # JUnit, many entry points.
	#     for MAIN_CLASS in org.junit.tests.running.core.SystemExitTest junit.tests.framework.AllTests junit.tests.AllTests junit.tests.runner.AllTests junit.tests.runner.TextFeedbackTest junit.tests.extensions.AllTests junit.samples.money.MoneyTest org.junit.runner.JUnitCore org.junit.runner.notification.RunListener junit.runner.Version junit.textui.TestRunner
	#     do
	# 	TMP="${BENCH}"
	# 	BENCH="${BENCH}-${MAIN_CLASS}"
	# 	runDoopFor "/usr/data/gfour/corpus/contents/213-JUnit/junit/junit4.11-SNAPSHOT/junit-4.11-SNAPSHOT.jar" "--main ${MAIN_CLASS}"
	# 	BENCH="${TMP}"
	#     done
	#     ;;
	# jsonrpc4j)
	#     # Jsonrpc4j test (assumes test method has been made implicit reachable in Doop).
	#     runDoopFor "/home/gfour/Downloads/proxy-benchmarks/jsonrpc4j/build/libs/jsonrpc4j-1.4.6.jar ${DOOP_BENCHMARKS}/proxies/jsonrpc4j/libs" ""
	#     ;;
	jconsole)
	    # OpenJDK benchmark: JConsole.
	    runDoopFor "${OPENJDK_DIR}/jconsole/jconsole.jar ${OPENJDK_DIR}/jconsole/tools.jar" "-platform java_8 ${DEBUG}"
	    # ${OPENJDK_DIR}/jconsole/jconsole.jar ${OPENJDK_DIR}/jconsole/tools.jar
	    ;;
	dacapo-bach-jython)
	    # The dacapo-bach/jython benchmark.
	    # runDoopFor "${DOOP_BENCHMARKS}/dacapo-2006/jython.jar" "-dacapo -platform java_6"
	    runDoopFor "${DOOP_BENCHMARKS}/dacapo-bach/jython.jar" "-dacapo-bach -platform java_6"
	    ;;
    esac

    # Other okhttp benchmarks:
    #   okhttp-crawler
    #   okhttp-sample-parent,
    #   okhttp-simple-client
    #   okhttp-static-server
    #   okhttp-slack
    #   okhttp-tests-AutobahnTester (--main okhttp3.AutobahnTester)
    #   okhttp-tests-ExternalHttp2Example (--main okhttp3.ExternalHttp2Example)
    #   okhttp-tests-HttpOverHttp2Test (--main okhttp3.HttpOverHttp2Test)
    #   okhttp-tests-CookieTest (--main okhttp3.CookieTest)
    #   okhttp-tests-CookiesTest (--main okhttp3.CookiesTest)
    #   okhttp-tests-CallTest (--main okhttp3.CallTest)
    #   okhttp-urlconnection (--main okhttp3.JavaNetCookieJar)

    # Other Google Guice benchmarks:
    #   guice-deps (--main
    #       com.google.inject.example.ClientServiceWithDependencyInjection)
    #   guice-factories (--main
    #       com.google.inject.example.ClientServiceWithFactories)
    #   guice-guice (--main com.google.inject.example.ClientServiceWithGuice)
    #   guice-defaults (--main
    #       com.google.inject.example.ClientServiceWithGuiceDefaults)

done
