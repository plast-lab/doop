#!/bin/bash

# Benchmark suite for dynamic proxies.

function runDoopFor {
    CMD="./doop -i $1 -a context-insensitive -id $2 --reflection --reflection-classic --reflection-substring-analysis --reflection-use-based-analysis --reflection-invent-unknown-objects --reflection-refined-objects $3 $4 |& tee doop-facts-$2.txt"
    echo ${CMD}
    eval ${CMD}
}

for proxySwitch in "" "-dynamic-proxies"
do
    # The simple empty program.
    # ID="dummy${proxySwitch}"
    # runDoopFor "/home/gfour/doop-benchmarks/proxies/dummy/Main.jar" $ID $proxySwitch "-platform java_8"

    # The proxy example.
    # ID="proxy-test${proxySwitch}"
    # runDoopFor "/home/gfour/doop-benchmarks/proxies/proxy-example-code/Main.jar" $ID $proxySwitch ""

    # The dacapo-bach benchmarks.
    # ID="dacapo-bach-jython${proxySwitch}"
    # runDoopFor "/opt/doop/benchmarks/dacapo-bach/jython/jython.jar" $ID $proxySwitch "-dacapo-bach -platform java_6"
    # ID="dacapo-9.12-bach${proxySwitch}"
    # runDoopFor "/home/gfour/Downloads/dacapo-bach/dacapo-9.12-bach.jar" $ID $proxySwitch ""

    # The okhttp benchmarks. The ALPN library is needed for resolution
    # of interfaces by the dynamic proxy.
    ALPN="/home/gfour/doop-benchmarks/proxies/okhttp/alpn-api-1.0.0.jar"
    ID="okhttp-simple-client${proxySwitch}"
    runDoopFor "/home/gfour/Downloads/proxy-benchmarks/okhttp/samples/simple-client/target/simple-client-3.6.0-SNAPSHOT-jar-with-dependencies.jar $ALPN" $ID $proxySwitch ""
    ID="okhttp-guide${proxySwitch}"
    runDoopFor "/home/gfour/Downloads/proxy-benchmarks/okhttp/samples/guide/target/guide-3.6.0-SNAPSHOT-jar-with-dependencies.jar $ALPN" $ID $proxySwitch ""
    ID="okhttp-crawler${proxySwitch}"
    runDoopFor "/home/gfour/Downloads/proxy-benchmarks/okhttp/samples/crawler/target/crawler-3.6.0-SNAPSHOT-jar-with-dependencies.jar $ALPN" $ID $proxySwitch ""
    ID="okhttp-sample-parent${proxySwitch}"
    runDoopFor "/home/gfour/Downloads/proxy-benchmarks/okhttp/samples/target/sample-parent-3.6.0-SNAPSHOT-jar-with-dependencies.jar $ALPN" $ID $proxySwitch ""
    ID="okhttp-static-server${proxySwitch}"
    runDoopFor "/home/gfour/Downloads/proxy-benchmarks/okhttp/samples/static-server/target/static-server-3.6.0-SNAPSHOT-jar-with-dependencies.jar $ALPN" $ID $proxySwitch ""
    ID="okhttp-slack${proxySwitch}"
    runDoopFor "/home/gfour/Downloads/proxy-benchmarks/okhttp/samples/slack/target/slack-3.6.0-SNAPSHOT-jar-with-dependencies.jar $ALPN" $ID $proxySwitch ""
    ID="okhttp-tests${proxySwitch}"
    runDoopFor "/home/gfour/Downloads/proxy-benchmarks/okhttp/okhttp-tests/target/okhttp-tests-3.6.0-SNAPSHOT-jar-with-dependencies.jar $ALPN" $ID $proxySwitch ""

done
