#!/bin/bash

# Benchmark suite for dynamic proxies.

DEBUG="-- -logLevel debugDetail@factbus"

OKDIR="${DOOP_BENCHMARKS}/proxies/okhttp"
GDIR="${DOOP_BENCHMARKS}/proxies/guice"
OPENJDK_DIR="${DOOP_BENCHMARKS}/openjdk/jconsole"

# Runs Doop for a benchmark. Arguments:
#   $1 - benchmark JAR
#   $2 - extra arguments (e.g. -platform java_6 or the DEBUG switch)
function runDoopFor {
    ID="${BENCH}${PROXY_SWITCH}"
    CMD="./doop -i $1 -a context-insensitive --reflection-classic --reflection-substring-analysis -id ${ID} ${PROXY_SWITCH} $2 -timeout 500 |& tee doop-facts-${ID}.txt"
    # CMD="./doop -i $1 -a context-insensitive -id ${ID} --reflection --reflection-classic --reflection-substring-analysis --reflection-speculative-use-based-analysis --reflection-invent-unknown-objects --reflection-refined-objects ${PROXY_SWITCH} $2 |& tee doop-facts-${ID}.txt"
    # CMD="./doop -i $1 -a context-insensitive --reflection-classic --reflection-substring-analysis --reflection-high-soundness-mode -id ${ID} ${PROXY_SWITCH} $2 -timeout 500 |& tee doop-facts-${ID}.txt"
    # CMD="./doop -i $1 -a context-insensitive -id ${ID} ${PROXY_SWITCH} $2 -timeout 500 |& tee doop-facts-${ID}.txt"
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

for PROXY_SWITCH in "" "-reflection-dynamic-proxies"
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
	    runDoopFor "${DOOP_BENCHMARKS}/challenges-corpus/aopTest-0.0.1-SNAPSHOT.jar" "--main creative.fire.aop.proxy.TaskProxy"
	    ;;
	okhttp-mockwebserver)
	    # OkHttp benchmark: mockwebserver.
	    runDoopFor "${OKDIR}/mockwebserver.jar" ""
	    ;;
	guice-jndi)
	    # Google Guice JNDI test.
	    runDoopFor "${GDIR}/core/target/guice-4.1.0-tests.jar ${GDIR}/core/target/guice-4.1.0-jar-with-dependencies.jar" "--main com.google.inject.example.JndiProviderClient"
	    ;;
	jconsole)
	    # OpenJDK benchmark: JConsole.
	    runDoopFor "${OPENJDK_DIR}/jconsole/jconsole.jar ${OPENJDK_DIR}/jconsole/tools.jar" "-platform java_8 ${DEBUG}"
	    # ${OPENJDK_DIR}/jconsole/jconsole.jar ${OPENJDK_DIR}/jconsole/tools.jar
	    ;;
	dacapo-bach-jython)
	    # The dacapo-bach/jython benchmark.
	    runDoopFor "${DOOP_BENCHMARKS}/dacapo-bach/jython/jython.jar" "-dacapo-bach -platform java_6"
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
