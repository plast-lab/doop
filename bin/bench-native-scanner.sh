#!/usr/bin/env bash

if [ "${DOOP_HOME}" == "" ]; then
    echo "Please set DOOP_HOME."
    exit
fi
if [ "${DOOP_BENCHMARKS}" == "" ]; then
    echo "Please set DOOP_BENCHMARKS."
    exit
fi
if [ "${SERVER_ANALYSIS_TESTS}" == "" ]; then
    echo "Please set SERVER_ANALYSIS_TESTS."
    exit
fi

JVM_NATIVE_CODE=${DOOP_HOME}/jvm8-native-code.jar

function measureRecall() {
    local ID_STATIC="$1"
    local ID_DYNAMIC="$2"
    local DYNAMIC_EDGES=${DOOP_HOME}/out/context-insensitive/${ID_DYNAMIC}/database/mainAnalysis.DynamicAppCallGraphEdgeFromNative.csv
    local SCANNER_EDGES=${DOOP_HOME}/out/context-insensitive/${ID_STATIC}/database/basic.AppCallGraphEdgeFromNativeMethod.csv
    local INTERSECTION_COUNT=$(comm -1 -2 <(sort -u ${DYNAMIC_EDGES}) <(sort -u ${SCANNER_EDGES}) | wc -l)
    local DYNAMIC_EDGES_COUNT=$(cat ${DYNAMIC_EDGES} | wc -l)
    echo "Dynamic edges: ${DYNAMIC_EDGES}"
    echo "Scanner edges: ${SCANNER_EDGES}"
    echo "${INTERSECTION_COUNT} / ${DYNAMIC_EDGES_COUNT}"
    python -c "print(str(100.0 * ${INTERSECTION_COUNT} / ${DYNAMIC_EDGES_COUNT}) + '%')"
}

function runDoop() {
    local INPUT="$1"
    local ID="$2"
    local PLATFORM="$3"
    local HPROF="$4"
    echo HPROF=${HPROF}
    if [ ! -f ${HPROF} ]; then
        echo "Error, file does not exist: ${HPROF}"
        return
    fi
    date
    local ID1="native-test-${ID}-scanner"
    local ID2="native-test-${ID}-heapdl"
    ./doop -i ${INPUT} -a context-insensitive --id ${ID1} --platform ${PLATFORM} --timeout 600 --scan-native-code |& tee ${ID1}.log
    ./doop -i ${INPUT} -a context-insensitive --id ${ID2} --platform ${PLATFORM} --timeout 600 --heapdl-file ${HPROF} |& tee ${ID2}.log
    measureRecall "${ID1}" "${ID2}"
}

# Generate java.hprof with "make capture_hprof".
runDoop ${SERVER_ANALYSIS_TESTS}/009-native/build/libs/009-native.jar 009-native java_8 ${SERVER_ANALYSIS_TESTS}/009-native/java.hprof

# Decompress com.instagram.android.hprof.gz with "gunzip".
runDoop ${DOOP_BENCHMARKS}/android-benchmarks/com.instagram.android_10.5.1-48243317_minAPI16\(armeabi-v7a\)\(320dpi\)_apkmirror.com.apk instagram android_25_fulljars ${DOOP_BENCHMARKS}/android-benchmarks/com.instagram.android.hprof
