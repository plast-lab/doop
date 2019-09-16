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
if [ "${XCORPUS_DIR}" == "" ]; then
    echo "Please set XCORPUS_DIR."
    exit
fi

# The "fulljars" platform is the full Android code, while "stubs"
# should only be used for recall calculation, not reachability metrics.
# ANDROID_PLATFORM=android_25_fulljars
ANDROID_PLATFORM=android_25_stubs

JVM_NATIVE_CODE=${DOOP_HOME}/jvm8-native-code.jar

function measureRecall() {
    local ID="$1"
    local ID_STATIC="$2"
    local ID_DYNAMIC="$3"
    # local DYNAMIC_EDGES=${DOOP_HOME}/out/context-insensitive/${ID_DYNAMIC}/database/mainAnalysis.DynamicAppCallGraphEdgeFromNative.csv
    local DYNAMIC_EDGES=${DOOP_HOME}/out/context-insensitive/${ID_DYNAMIC}/database/mainAnalysis.DynamicAppNativeCodeTarget.csv
    # local SCANNER_EDGES=${DOOP_HOME}/out/context-insensitive/${ID_STATIC}/database/basic.AppCallGraphEdgeFromNativeMethod.csv
    local SCANNER_EDGES=${DOOP_HOME}/out/context-insensitive/${ID_STATIC}/database/mainAnalysis.ReachableAppMethodFromNativeCode.csv
    local INTERSECTION_FILE="dynamic-scanner-intersection-${ID}.log"
    local MISSED_FILE="missed-methods-${ID}.log"

    echo "Intersection file: ${INTERSECTION_FILE}"
    echo "Dynamic edges: ${DYNAMIC_EDGES}"
    echo "Scanner edges: ${SCANNER_EDGES}"

    echo "Calculating recall..."
    comm -1 -2 <(sort -u ${DYNAMIC_EDGES}) <(sort -u ${SCANNER_EDGES}) > ${INTERSECTION_FILE}
    local INTERSECTION_COUNT=$(cat ${INTERSECTION_FILE} | wc -l)
    local DYNAMIC_EDGES_COUNT=$(cat ${DYNAMIC_EDGES} | wc -l)
    echo "${INTERSECTION_COUNT} / ${DYNAMIC_EDGES_COUNT}"
    python -c "print(str(100.0 * ${INTERSECTION_COUNT} / ${DYNAMIC_EDGES_COUNT}) + '%')"

    echo "Calculating missed methods (file: ${MISSED_FILE})"
    comm -2 -3 <(sort -u ${DYNAMIC_EDGES}) <(sort -u ${SCANNER_EDGES}) > ${MISSED_FILE}
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
    measureRecall "${ID}" "${ID1}" "${ID2}"
}

function analyzeAspectJ() {
    local DIR="${XCORPUS_DIR}/data/qualitas_corpus_20130901/aspectj-1.6.9"
    local APP_INPUTS="${DIR}/project/unpacked/aspectjweaver1.6.9.jar ${DIR}/project/unpacked/aspectjtools1.6.9/ant_tasks/resources-ant.jar ${DIR}/project/unpacked/aspectjrt1.6.9.jar ${DIR}/project/unpacked/aspectjtools1.6.9.jar ${DIR}/project/unpacked/org.aspectj.matcher-1.6.9.jar ${DIR}/project/default-lib/eclipse.jar"
    local TESTS="${DIR}/.xcorpus/org.aspectj.matcher-1.6.9-tests.jar ${DIR}/.xcorpus/aspectjtools1.6.9-tests.jar ${DIR}/.xcorpus/aspectjweaver1.6.9-tests.jar ${DIR}/.xcorpus/aspectjrt1.6.9-tests.jar"
    local LIBS="${DIR}/.xcorpus/lib/avalon-framework-4.1.3.jar ${DIR}/.xcorpus/lib/commons-logging-1.1.1.jar ${DIR}/.xcorpus/lib/servlet-api-2.3.jar ${DIR}/.xcorpus/lib/commands-3.3.0-I20070605-0010.jar ${DIR}/.xcorpus/lib/text-3.3.0-v20070606-0010.jar ${DIR}/.xcorpus/lib/osgi-3.9.1-v20130814-1242.jar ${DIR}/.xcorpus/lib/common-3.6.200-v20130402-1505.jar ${DIR}/.xcorpus/lib/ant-launcher-1.8.1.jar ${DIR}/.xcorpus/lib/asm-3.2.jar ${DIR}/.xcorpus/lib/logkit-1.0.1.jar ${DIR}/.xcorpus/lib/ant-1.8.1.jar ${DIR}/.xcorpus/lib/log4j-1.2.12.jar ${DIR}/.xcorpus/build/main/aspectjtools1.6.9/ant_tasks/resources-ant.jar"
    local NATIVE_LIB="${XCORPUS_DIR}/data/qualitas_corpus_20130901/aspectj-1.6.9/native/x86_64-1.0.100-v20070510.jar"
    local ID="aspectj-native"
    echo "./doop -i ${APP_INPUTS} ${TESTS} ${LIBS} ${NATIVE_LIB} -a context-insensitive --id ${ID} --scan-native-code --discover-tests --timeout 240 |& tee ${ID}.log"
}

# Generate java.hprof with "make capture_hprof".
runDoop ${SERVER_ANALYSIS_TESTS}/009-native/build/libs/009-native.jar 009-native java_8 ${SERVER_ANALYSIS_TESTS}/009-native/java.hprof

# Instagram.
runDoop ${DOOP_BENCHMARKS}/android-benchmarks/com.instagram.android_10.5.1-48243323_minAPI16_x86_nodpi_apkmirror.com.apk instagram ${ANDROID_PLATFORM} ${DOOP_BENCHMARKS}/android-benchmarks/com.instagram.android.hprof.gz

# Chrome.
runDoop ${DOOP_BENCHMARKS}/android-benchmarks/com.android.chrome_57.0.2987.132-298713212_minAPI24_x86_nodpi_apkmirror.com.apk chrome ${ANDROID_PLATFORM} ${DOOP_BENCHMARKS}/android-benchmarks/com.android.chrome.hprof.gz

# Androidterm.
runDoop ${DOOP_BENCHMARKS}/android-benchmarks/jackpal.androidterm-1.0.70-71-minAPI4.apk androidterm ${ANDROID_PLATFORM} ${DOOP_BENCHMARKS}/android-benchmarks/jackpal.androidterm.hprof.gz

analyzeAspectJ
