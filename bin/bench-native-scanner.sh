#!/usr/bin/env bash

CURRENT_DIR="$(pwd)"
# echo "CURRENT_DIR=${CURRENT_DIR}"

if [ "${DOOP_HOME}" == "" ]; then
    echo "ERROR: Please set DOOP_HOME."
    exit
fi

ANALYSIS=context-insensitive
TIMEOUT=600
STRING_DISTANCE1=40
STRING_DISTANCE2=2000
BASE_GLOBAL_OPTS="--timeout ${TIMEOUT} --no-standard-exports --Xlow-mem --stats none --extra-logic ${DOOP_HOME}/souffle-logic/addons/testing/native-tests.dl --dont-cache-facts"

# The "fulljars" platform is the full Android code.
ANDROID_PLATFORM=android_25_fulljars

function checkXCorpusEnv() {
    if [ "${XCORPUS_DIR}" == "" ]; then
        echo "ERROR: Please set XCORPUS_DIR."
        exit
    fi
    if [ "${XCORPUS_EXT_DIR}" == "" ]; then
        echo "ERROR: Please set XCORPUS_EXT_DIR."
        exit
    fi
}

function setIntersection() {
    comm -1 -2 <(sort -u "$1") <(sort -u "$2")
}

function setDifference() {
    comm -2 -3 <(sort -u "$1") <(sort -u "$2")
}

function calcIncrease() {
    local ARG1=$(echo "$1" | sed -e 's/,/./g')
    local ARG2=$(echo "$2" | sed -e 's/,/./g')
    python -c "print('%.2f' % (100.0 * (${ARG2} - ${ARG1}) / ${ARG1}) + '%')"
}

function printStatsRow() {
    local BENCHMARK="$1"
    local ID_BASE="$2"
    local ID_SCANNER="$3"
    local ID_DYNAMIC="$4"

    local DYNAMIC_METHODS=${DOOP_HOME}/out/${ID_DYNAMIC}/database/DynamicAppNativeCodeTarget.csv
    local SCANNER_METHODS=${DOOP_HOME}/out/${ID_SCANNER}/database/AppReachable.csv
    local MISSED_FILE="${CURRENT_DIR}/missed-methods-${ID_SCANNER}.log"

    if [ ! -f "${SCANNER_METHODS}" ]; then
        echo -e "| ${BENCHMARK}\t| Missing file ${SCANNER_METHODS}"
        return
    fi

    local BASE_INTERSECTION_FILE="${CURRENT_DIR}/dynamic-scanner-intersection-${ID_BASE}.log"
    local BASE_APP_REACHABLE_FILE=${DOOP_HOME}/out/${ID_BASE}/database/AppReachable.csv
    local BASE_APP_REACHABLE=$(cat ${BASE_APP_REACHABLE_FILE} | wc -l)
    local SCANNER_APP_REACHABLE=$(cat ${SCANNER_METHODS} | wc -l)

    if [ -f "${DYNAMIC_METHODS}" ]; then
        local DYNAMIC_METHODS_COUNT=$(cat ${DYNAMIC_METHODS} | wc -l)
        # 1. Calculate recall of the "base" analysis.
        setIntersection ${DYNAMIC_METHODS} ${BASE_APP_REACHABLE_FILE} > ${BASE_INTERSECTION_FILE}
        local BASE_INTERSECTION_COUNT=$(cat ${BASE_INTERSECTION_FILE} | wc -l)
        local BASE_RECALL="${BASE_INTERSECTION_COUNT}/${DYNAMIC_METHODS_COUNT} = "$(python -c "print('%.2f' % (100.0 * ${BASE_INTERSECTION_COUNT} / ${DYNAMIC_METHODS_COUNT}) + '%')")
        # 2. Calculate recall of the "scanner" analysis.
        local INTERSECTION_FILE="${CURRENT_DIR}/dynamic-scanner-intersection-${ID_SCANNER}.log"
        setIntersection ${DYNAMIC_METHODS} ${SCANNER_METHODS} > ${INTERSECTION_FILE}
        local INTERSECTION_COUNT=$(cat ${INTERSECTION_FILE} | wc -l)
        local RECALL="${INTERSECTION_COUNT}/${DYNAMIC_METHODS_COUNT} = "$(python -c "print('%.2f' % (100.0 * ${INTERSECTION_COUNT} / ${DYNAMIC_METHODS_COUNT}) + '%')")

        comm -2 -3 <(sort -u ${DYNAMIC_METHODS}) <(sort -u ${SCANNER_METHODS}) > ${MISSED_FILE}
    else
        local BASE_RECALL='n/a'
        local RECALL='n/a'
    fi

    local APP_METHOD_COUNT=$(cat ${DOOP_HOME}/out/${ID_BASE}/database/ApplicationMethod.csv | wc -l)
    # echo "Application methods: ${APP_METHOD_COUNT}"
    local APP_REACHABLE_DELTA="${BASE_APP_REACHABLE} -> ${SCANNER_APP_REACHABLE}: "$(calcIncrease ${BASE_APP_REACHABLE} ${SCANNER_APP_REACHABLE})
    # echo "App-reachable increase over base: ${APP_REACHABLE_DELTA}"

    # Use 'xargs' to remove whitespace.
    local BASE_ANALYSIS_TIME=$(grep -F 'analysis execution time (sec)' ${CURRENT_DIR}/${ID_BASE}.log | cut -d ')' -f 2 | xargs)
    local SCANNER_ANALYSIS_TIME=$(grep -F 'analysis execution time (sec)' ${CURRENT_DIR}/${ID_SCANNER}.log | cut -d ')' -f 2 | xargs)
    local BASE_FACTS_TIME=$(grep -F 'Soot fact generation time:' ${CURRENT_DIR}/${ID_BASE}.log | cut -d ':' -f 2 | xargs)
    local SCANNER_FACTS_TIME=$(grep -F 'Soot fact generation time:' ${CURRENT_DIR}/${ID_SCANNER}.log | cut -d ':' -f 2 | xargs)
    local ANALYSIS_TIME_DELTA="${BASE_ANALYSIS_TIME} -> ${SCANNER_ANALYSIS_TIME}: "$(calcIncrease ${BASE_ANALYSIS_TIME} ${SCANNER_ANALYSIS_TIME})
    local FACTS_TIME_DELTA="${BASE_FACTS_TIME} -> ${SCANNER_FACTS_TIME} "$(calcIncrease ${BASE_FACTS_TIME} ${SCANNER_FACTS_TIME})
    # echo "Analysis time increase over base: ${ANALYSIS_TIME_DELTA}"

    local SCANNER_ENTRY_POINTS=${DOOP_HOME}/out/${ID_SCANNER}/database/mainAnalysis.ReachableAppMethodFromNativeCode.csv
    local ADDED_ENTRY_POINTS_FILE=${CURRENT_DIR}/extra-entry-points-${ID_SCANNER}.log
    setDifference ${SCANNER_ENTRY_POINTS} ${BASE_APP_REACHABLE_FILE} > ${ADDED_ENTRY_POINTS_FILE}
    local ADDED_ENTRY_POINTS=$(cat ${ADDED_ENTRY_POINTS_FILE} | wc -l)

    echo -e "| ${BENCHMARK}\t| ${APP_METHOD_COUNT}\t| ${BASE_RECALL}\t| ${RECALL}\t| ${APP_REACHABLE_DELTA}\t| ${ANALYSIS_TIME_DELTA}\t| ${FACTS_TIME_DELTA}\t| ${ADDED_ENTRY_POINTS}\t|"
}

function setIDs() {
    local BENCHMARK="$1"
    ID_BASE="native-test-${BENCHMARK}-base"
    ID_SCANNER="native-test-${BENCHMARK}-scanner"
    ID_SCANNER_LOCAL_OBJ="${ID_SCANNER}-loc-obj"
    ID_SCANNER_LOCAL_RAD="${ID_SCANNER}-loc-rad"
    ID_SCANNER_SMART="${ID_SCANNER}-smart"
    ID_SCANNER_OFFSETS1="${ID_SCANNER}-dist-${STRING_DISTANCE1}"
    ID_SCANNER_OFFSETS2="${ID_SCANNER}-dist-${STRING_DISTANCE2}"
    ID_HEAPDL="native-test-${BENCHMARK}-heapdl"
}

function deleteFacts() {
    local FACTS_DIR="${DOOP_HOME}/out/$1/facts"
    echo "Deleting facts: ${FACTS_DIR}"
    rm -rf "${FACTS_DIR}"
}

function runDoop() {
    local INPUT="$1"
    local BENCHMARK="$2"
    local PLATFORM="$3"
    local HPROF="$4"
    local BACKEND="$5"
    # echo HPROF="${HPROF}"
    # if [ ! -f ${HPROF} ]; then
    #     echo "Error, file does not exist: ${HPROF}"
    #     return
    # fi
    setIDs "${BENCHMARK}"
    pushd ${DOOP_HOME} &> /dev/null
    date
    BASE_OPTS="--platform ${PLATFORM} ${BASE_GLOBAL_OPTS}"
    # 1. Base analysis.
    ${DOOP} -i ${INPUT} -a ${ANALYSIS} --id ${ID_BASE} ${BASE_OPTS} |& tee ${CURRENT_DIR}/${ID_BASE}.log
    deleteFacts ${ID_BASE}
    if [ "${HPROF}" != "" ]; then
        # 2. HeapDL analysis, for comparison.
        ${DOOP} -i ${INPUT} -a ${ANALYSIS} --id ${ID_HEAPDL} ${BASE_OPTS} --heapdl-file ${HPROF} --featherweight-analysis |& tee ${CURRENT_DIR}/${ID_HEAPDL}.log
	deleteFacts ${ID_HEAPDL}
    fi
    # 3. Native scanner, default mode.
    ${DOOP} -i ${INPUT} -a ${ANALYSIS} --id ${ID_SCANNER} ${BASE_OPTS} --scan-native-code --native-code-backend ${BACKEND} |& tee ${CURRENT_DIR}/${ID_SCANNER}.log
    deleteFacts ${ID_SCANNER}
    popd &> /dev/null
}

function printLine() {
    for i in $(seq 1 $1); do
        echo -n '-'
    done
    echo
}

function printStatsTable() {
    let COL1_END="18"
    let COL2_END="${COL1_END} + 10"
    let COL3_END="${COL2_END} + 17"
    let COL4_END="${COL3_END} + 22"
    let COL5_END="${COL4_END} + 30"
    let COL6_END="${COL5_END} + 25"
    let COL7_END="${COL6_END} + 25"
    let COL8_END="${COL7_END} + 10"
    local LAST_COL=${COL8_END}
    tabs ${COL1_END},${COL2_END},${COL3_END},${COL4_END},${COL5_END},${COL6_END},${COL7_END},${COL8_END}
    printLine ${LAST_COL}
    echo -e "| Benchmark\t| App    \t| Base  \t| Recall\t| +App-reachable    \t| +Analysis time    \t| +Factgen time\t| +Entry\t|"
    echo -e "|          \t| methods\t| recall\t|       \t|  (incr. over base)\t|  (incr. over base)\t|              \t|  points\t|"
    printLine ${LAST_COL}
    # for BENCHMARK in "chrome" "instagram" "009-native" "aspectj-1.6.9" "log4j-1.2.16" "lucene-4.3.0" "tomcat-7.0.2"
    # for BENCHMARK in "chrome" "instagram" "aspectj-1.6.9" "log4j-1.2.16" "lucene-4.3.0" "tomcat-7.0.2"
    for BENCHMARK in $*
    do
        setIDs "${BENCHMARK}"
        local ID_STATIC="${ID_SCANNER}"
        printStatsRow "${BENCHMARK}" "${ID_BASE}" "${ID_STATIC}" "${ID_HEAPDL}"
        printLine ${LAST_COL}
    done
}

function analyzeAspectJ() {
    checkXCorpusEnv
    local BASE_DIR="${XCORPUS_DIR}/data/qualitas_corpus_20130901/aspectj-1.6.9"
    # APP_INPUTS="${XCORPUS_EXT_DIR}/repackaged/aspectj-1.6.9 ${XCORPUS_EXT_DIR}/native/aspectj/x86_64-1.0.100-v20070510.jar"
    APP_INPUTS="${XCORPUS_EXT_DIR}/repackaged/aspectj-1.6.9 ${XCORPUS_EXT_DIR}/native/aspectj/localfile_1_0_0.dll.zip"
    analyzeXCorpusBenchmark "aspectj-1.6.9"
}

function analyzeLucene() {
    checkXCorpusEnv
    local BASE_DIR="${XCORPUS_DIR}/data/qualitas_corpus_20130901/lucene-4.3.0"
    APP_INPUTS="${XCORPUS_EXT_DIR}/repackaged/lucene-4.3.0 ${XCORPUS_EXT_DIR}/native/lucene/lucene-misc-4.3.0/org/apache/lucene/store/libNativePosixUtil.so.jar"
    analyzeXCorpusBenchmark "lucene-4.3.0"
}

function analyzeLog4J() {
    checkXCorpusEnv
    local BASE_DIR="${XCORPUS_DIR}/data/qualitas_corpus_20130901/log4j-1.2.16"
    APP_INPUTS="${BASE_DIR}/project/bin.zip ${BASE_DIR}/project/builtin-tests.zip ${BASE_DIR}/.xcorpus/evosuite-tests.zip"
    analyzeXCorpusBenchmark "log4j-1.2.16"
}

function analyzeTomcat() {
    checkXCorpusEnv
    local BASE_DIR="${XCORPUS_DIR}/data/qualitas_corpus_20130901/tomcat-7.0.2"
    APP_INPUTS="${XCORPUS_EXT_DIR}/repackaged/tomcat-7.0.2 ${BASE_DIR}/project/builtin-tests.zip ${XCORPUS_EXT_DIR}/native/tomcat/win64/tcnative.dll_win64_x64.jar ${XCORPUS_EXT_DIR}/native/tomcat/mandriva/libtcnative-1.so.jar"
    analyzeXCorpusBenchmark "tomcat-7.0.2"
}

function analyzeXCorpusBenchmark() {
    local BENCHMARK="$1"
    local BASE_OPTS="-i ${APP_INPUTS} -a ${ANALYSIS} --discover-main-methods --discover-tests --main DUMMY ${BASE_GLOBAL_OPTS}"

    if [ "${XCORPUS_DIR}" == "" ]; then
        echo "ERROR: cannot analyze benchmark '${BENCHMARK}', please set environment variable XCORPUS_DIR to point to the XCorpus directory."
        exit
    elif [ "${XCORPUS_EXT_DIR}" == "" ]; then
        echo "ERROR: cannot analyze benchmark '${BENCHMARK}', please set environment variable XCORPUS_EXT_DIR to point to the XCorpus extension directory."
        exit
    else
        echo "Original XCorpus directory: ${XCORPUS_DIR}"
        echo "XCorpus native extension directory: ${XCORPUS_EXT_DIR}"
    fi

    setIDs "${BENCHMARK}"
    pushd ${DOOP_HOME} &> /dev/null
    date
    set -x
    # 1. Base analysis.
    ${DOOP} ${BASE_OPTS} --id ${ID_BASE} |& tee ${CURRENT_DIR}/${ID_BASE}.log
    deleteFacts ${ID_BASE}
    # 2. Native scanner, default mode.
    # ${DOOP} ${BASE_OPTS} --id ${ID_SCANNER} --scan-native-code |& tee ${CURRENT_DIR}/${ID_SCANNER}.log
    ${DOOP} ${BASE_OPTS} --id ${ID_SCANNER} --scan-native-code --native-code-backend radare |& tee ${CURRENT_DIR}/${ID_SCANNER}.log
    deleteFacts ${ID_SCANNER}
    set +x
    popd &> /dev/null
}


# Put external benchmarks in this function and use "external" command-line option.
function analyzeExternalBenchmarks() {
    runDoop ${DOOP_BENCHMARKS}/android-benchmarks/bm1.apk bm1 ${ANDROID_PLATFORM} "" "radare"
    printStatsTable bm1
}

trap "exit" INT

if [ "${DOOP}" == "" ]; then
    DOOP='./doop'
fi
echo "Using Doop executable: ${DOOP}"

if [ "$1" == "report" ]; then
    RUN_ANALYSIS=0
elif [ "$1" == "analyze" ]; then
    RUN_ANALYSIS=1
elif [ "$1" == "external" ]; then
    analyzeExternalBenchmarks
    exit
else
    echo "Usage: bench-native-scanner.sh [analyze|report|external]"
    echo ""
    echo "  analyze      analyze standard benchmarks"
    echo "  report       print analysis report for standard benchmarks"
    echo "  external     analyze and report results for external benchmarks"
    echo "               (see analyzeExternalBenchmarks() in the script)"
    exit
fi

if [ "${RUN_ANALYSIS}" == "1" ]; then

    # Control benchmark, used for debugging.
    # # Generate java.hprof with "make capture_hprof".
    # if [ "${SERVER_ANALYSIS_TESTS}" == "" ]; then
    # 	echo "ERROR: Please set SERVER_ANALYSIS_TESTS."
    #   exit
    # fi
    # runDoop ${SERVER_ANALYSIS_TESTS}/009-native/build/libs/009-native.jar 009-native java_8 ${SERVER_ANALYSIS_TESTS}/009-native/java.hprof

    if [ "${DOOP_BENCHMARKS}" == "" ]; then
        echo "ERROR: Please set DOOP_BENCHMARKS."
        exit
    fi

    # Chrome.
    runDoop ${DOOP_BENCHMARKS}/android-benchmarks/com.android.chrome_57.0.2987.132-298713212_minAPI24_x86_nodpi_apkmirror.com.apk chrome ${ANDROID_PLATFORM} ${DOOP_BENCHMARKS}/android-benchmarks/com.android.chrome.hprof.gz binutils

    # Instagram.
    runDoop ${DOOP_BENCHMARKS}/android-benchmarks/com.instagram.android_10.5.1-48243323_minAPI16_x86_nodpi_apkmirror.com.apk instagram ${ANDROID_PLATFORM} ${DOOP_BENCHMARKS}/android-benchmarks/com.instagram.android.hprof.gz binutils

    # AspectJ.
    analyzeAspectJ

    # Lucene.
    analyzeLucene

    # Log4j.
    analyzeLog4J

    # Tomcat.
    analyzeTomcat
fi

printStatsTable "chrome" "instagram" "aspectj-1.6.9" "log4j-1.2.16" "lucene-4.3.0" "tomcat-7.0.2"
