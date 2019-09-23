#!/usr/bin/env bash

CURRENT_DIR="$(pwd)"
# echo "CURRENT_DIR=${CURRENT_DIR}"

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

ANALYSIS=context-insensitive
TIMEOUT=600
STRING_DISTANCE1=20
STRING_DISTANCE2=2000

RUN_ANALYSIS=1
# RUN_ANALYSIS=0

# The "fulljars" platform is the full Android code, while "stubs"
# should only be used for recall calculation, not reachability metrics.
ANDROID_PLATFORM=android_25_fulljars
# ANDROID_PLATFORM=android_25_stubs

JVM_NATIVE_CODE=${DOOP_HOME}/jvm8-native-code.jar

function setIntersection() {
    comm -1 -2 <(sort -u "$1") <(sort -u "$2")
}

function setDifference() {
    comm -2 -3 <(sort -u "$1") <(sort -u "$2")
}

function calcIncrease() {
    python -c "print('%.2f' % (100.0 * ($2 - $1) / $1) + '%')"
}

function printStatsRow() {
    local BENCHMARK="$1"
    local ID_BASE="$2"
    local ID_SCANNER="$3"
    local ID_DYNAMIC="$4"
    local MODE="$5"

    # local DYNAMIC_METHODS=${DOOP_HOME}/out/${ANALYSIS}/${ID_DYNAMIC}/database/mainAnalysis.DynamicAppCallGraphEdgeFromNative.csv
    local DYNAMIC_METHODS=${DOOP_HOME}/out/${ANALYSIS}/${ID_DYNAMIC}/database/mainAnalysis.DynamicAppNativeCodeTarget.csv
    # local SCANNER_METHODS=${DOOP_HOME}/out/${ANALYSIS}/${ID_SCANNER}/database/basic.AppCallGraphEdgeFromNativeMethod.csv
    local SCANNER_METHODS=${DOOP_HOME}/out/${ANALYSIS}/${ID_SCANNER}/database/Stats_Simple_Application_ReachableMethod.csv
    local MISSED_FILE="${CURRENT_DIR}/missed-methods-${ID_SCANNER}.log"

    # echo "== Benchmark: ${BENCHMARK} (static scanner mode: ${ID_SCANNER}) =="
    # echo "Intersection file: ${INTERSECTION_FILE}"
    # echo "Dynamic methods: ${DYNAMIC_METHODS}"
    # echo "Scanner methods: ${SCANNER_METHODS}"
    # echo "Missed methods: ${MISSED_FILE}"

    local DYNAMIC_METHODS_COUNT=$(cat ${DYNAMIC_METHODS} | wc -l)
    # 1. Calculate recall of the "base" analysis.
    local BASE_INTERSECTION_FILE="${CURRENT_DIR}/dynamic-scanner-intersection-${ID_BASE}.log"
    local BASE_APP_REACHABLE_FILE=${DOOP_HOME}/out/${ANALYSIS}/${ID_BASE}/database/Stats_Simple_Application_ReachableMethod.csv
    setIntersection ${DYNAMIC_METHODS} ${BASE_APP_REACHABLE_FILE} > ${BASE_INTERSECTION_FILE}
    local BASE_INTERSECTION_COUNT=$(cat ${BASE_INTERSECTION_FILE} | wc -l)
    local BASE_RECALL="${BASE_INTERSECTION_COUNT} / ${DYNAMIC_METHODS_COUNT} = "$(python -c "print('%.2f' % (100.0 * ${BASE_INTERSECTION_COUNT} / ${DYNAMIC_METHODS_COUNT}) + '%')")
    # 2. Calculate recall of the "scanner" analysis.
    local INTERSECTION_FILE="${CURRENT_DIR}/dynamic-scanner-intersection-${ID_SCANNER}.log"
    setIntersection ${DYNAMIC_METHODS} ${SCANNER_METHODS} > ${INTERSECTION_FILE}
    local INTERSECTION_COUNT=$(cat ${INTERSECTION_FILE} | wc -l)
    local RECALL="${INTERSECTION_COUNT} / ${DYNAMIC_METHODS_COUNT} = "$(python -c "print('%.2f' % (100.0 * ${INTERSECTION_COUNT} / ${DYNAMIC_METHODS_COUNT}) + '%')")

    comm -2 -3 <(sort -u ${DYNAMIC_METHODS}) <(sort -u ${SCANNER_METHODS}) > ${MISSED_FILE}

    local BASE_APP_REACHABLE=$(cat ${BASE_APP_REACHABLE_FILE} | wc -l)
    local SCANNER_APP_REACHABLE=$(cat ${SCANNER_METHODS} | wc -l)
    local APP_METHOD_COUNT=$(cat ${DOOP_HOME}/out/${ANALYSIS}/${ID_BASE}/database/ApplicationMethod.csv | wc -l)
    # echo "Application methods: ${APP_METHOD_COUNT}"
    local APP_REACHABLE_DELTA="(${BASE_APP_REACHABLE} -> ${SCANNER_APP_REACHABLE}): "$(calcIncrease ${BASE_APP_REACHABLE} ${SCANNER_APP_REACHABLE})
    # echo "App-reachable increase over base: ${APP_REACHABLE_DELTA}"

    # Use 'xargs' to remove whitespace.
    local BASE_ANALYSIS_TIME=$(grep -F 'analysis execution time (sec)' ${CURRENT_DIR}/${ID_BASE}.log | cut -d ')' -f 2 | xargs)
    local SCANNER_ANALYSIS_TIME=$(grep -F 'analysis execution time (sec)' ${CURRENT_DIR}/${ID_SCANNER}.log | cut -d ')' -f 2 | xargs)
    local BASE_FACTS_TIME=$(grep -F 'Soot fact generation time:' ${CURRENT_DIR}/${ID_BASE}.log | cut -d ':' -f 2 | xargs)
    local SCANNER_FACTS_TIME=$(grep -F 'Soot fact generation time:' ${CURRENT_DIR}/${ID_SCANNER}.log | cut -d ':' -f 2 | xargs)
    local ANALYSIS_TIME_DELTA="(${BASE_ANALYSIS_TIME} -> ${SCANNER_ANALYSIS_TIME}): "$(calcIncrease ${BASE_ANALYSIS_TIME} ${SCANNER_ANALYSIS_TIME})
    local FACTS_TIME_DELTA="(${BASE_FACTS_TIME} -> ${SCANNER_FACTS_TIME}): "$(calcIncrease ${BASE_FACTS_TIME} ${SCANNER_FACTS_TIME})
    # echo "Analysis time increase over base: ${ANALYSIS_TIME_DELTA}"

    local SCANNER_ENTRY_POINTS=${DOOP_HOME}/out/${ANALYSIS}/${ID_SCANNER}/database/mainAnalysis.ReachableAppMethodFromNativeCode.csv
    local ADDED_ENTRY_POINTS=$(setDifference ${SCANNER_ENTRY_POINTS} ${BASE_APP_REACHABLE_FILE} | wc -l)

    echo -e "| ${BENCHMARK} \t| ${MODE} \t| ${APP_METHOD_COUNT} \t| ${BASE_RECALL} \t| ${RECALL} \t| ${APP_REACHABLE_DELTA} \t| ${ANALYSIS_TIME_DELTA} \t| ${FACTS_TIME_DELTA} \t| ${ADDED_ENTRY_POINTS} \t|"
}

function setIDs() {
    local BENCHMARK="$1"
    ID_BASE="native-test-${BENCHMARK}-base"
    ID_SCANNER="native-test-${BENCHMARK}-scanner"
    ID_SCANNER_LOCAL="${ID_SCANNER}-localized"
    ID_SCANNER_SMART="${ID_SCANNER}-smart"
    ID_SCANNER_OFFSETS1="${ID_SCANNER}-dist-${STRING_DISTANCE1}"
    ID_SCANNER_OFFSETS2="${ID_SCANNER}-dist-${STRING_DISTANCE2}"
    ID_HEAPDL="native-test-${BENCHMARK}-heapdl"
}

function runDoop() {
    local INPUT="$1"
    local BENCHMARK="$2"
    local PLATFORM="$3"
    local HPROF="$4"
    # echo HPROF="${HPROF}"
    # if [ ! -f ${HPROF} ]; then
    #     echo "Error, file does not exist: ${HPROF}"
    #     return
    # fi
    setIDs "${BENCHMARK}"
    pushd ${DOOP_HOME} &> /dev/null
    date
    # 1. Base analysis.
    ./doop -i ${INPUT} -a ${ANALYSIS} --id ${ID_BASE} --platform ${PLATFORM} --timeout ${TIMEOUT} |& tee ${CURRENT_DIR}/${ID_BASE}.log
    # 2. HeapDL analysis, for comparison.
    ./doop -i ${INPUT} -a ${ANALYSIS} --id ${ID_HEAPDL} --platform ${PLATFORM} --timeout ${TIMEOUT} --heapdl-file ${HPROF} |& tee ${CURRENT_DIR}/${ID_HEAPDL}.log
    # 3. Native scanner, default mode.
    ./doop -i ${INPUT} -a ${ANALYSIS} --id ${ID_SCANNER} --platform ${PLATFORM} --timeout ${TIMEOUT} --scan-native-code |& tee ${CURRENT_DIR}/${ID_SCANNER}.log
    # 4. Native scanner, use only localized strings.
    ./doop -i ${INPUT} -a ${ANALYSIS} --id ${ID_SCANNER_LOCAL} --platform ${PLATFORM} --timeout ${TIMEOUT} --scan-native-code --only-precise-native-strings |& tee ${CURRENT_DIR}/${ID_SCANNER_LOCAL}.log
    # 5. Native scanner, "smart native targets" mode.
    ./doop -i ${INPUT} -a ${ANALYSIS} --id ${ID_SCANNER_SMART} --platform ${PLATFORM} --timeout ${TIMEOUT} --scan-native-code --smart-native-targets |& tee ${CURRENT_DIR}/${ID_SCANNER_SMART}.log
    # 6. Native scanner, "use string locality" mode.
    ./doop -i ${INPUT} -a ${ANALYSIS} --id ${ID_SCANNER_OFFSETS1} --platform ${PLATFORM} --timeout ${TIMEOUT} --scan-native-code --use-string-locality --native-strings-distance ${STRING_DISTANCE1} |& tee ${CURRENT_DIR}/${ID_SCANNER_OFFSETS1}.log
    if [ "${BENCHMARK}" == "chrome" ]; then
        ./doop -i ${INPUT} -a ${ANALYSIS} --id ${ID_SCANNER_OFFSETS2} --platform ${PLATFORM} --timeout ${TIMEOUT} --scan-native-code --use-string-locality --native-strings-distance ${STRING_DISTANCE2} |& tee ${CURRENT_DIR}/${ID_SCANNER_OFFSETS2}.log
    fi
    popd &> /dev/null
}

function printLine() {
    for i in $(seq 1 $1); do
        echo -n '-'
    done
    echo
}

function printStatsTable() {
    local LAST_COL=174
    tabs 16,30,41,61,83,112,125,137,143,152,163,${LAST_COL}
    printLine ${LAST_COL}
    echo -e "| Benchmark \t| Mode \t| App     \t| Base   \t| Recall \t| +App-reachable     \t| +Analysis time     \t| +Factgen time \t| +entry \t|"
    echo -e "|           \t|      \t| methods \t| recall \t|        \t|  (incr. over base) \t|  (incr. over base) \t|               \t|  points\t|"
    printLine ${LAST_COL}
    for BENCHMARK in androidterm chrome instagram 009-native
    do
        setIDs "${BENCHMARK}"
        if [ "${BENCHMARK}" == "chrome" ]; then
            MODES=( "" "-localized" "-smart" "-dist-${STRING_DISTANCE1}" "-dist-${STRING_DISTANCE2}" )
        else
            MODES=( "" "-localized" "-smart" "-dist-${STRING_DISTANCE1}" )
        fi
        for MODE in "${MODES[@]}"
        do
            local ID_STATIC="${ID_SCANNER}${MODE}"
            printStatsRow "${BENCHMARK}" "${ID_BASE}" "${ID_STATIC}" "${ID_HEAPDL}" "${MODE}"
        done
        printLine ${LAST_COL}
    done
}

function analyzeAspectJ() {
    local DIR="${XCORPUS_DIR}/data/qualitas_corpus_20130901/aspectj-1.6.9"
    local APP_INPUTS="${DIR}/project/unpacked/aspectjweaver1.6.9.jar ${DIR}/project/unpacked/aspectjtools1.6.9/ant_tasks/resources-ant.jar ${DIR}/project/unpacked/aspectjrt1.6.9.jar ${DIR}/project/unpacked/aspectjtools1.6.9.jar ${DIR}/project/unpacked/org.aspectj.matcher-1.6.9.jar ${DIR}/project/default-lib/eclipse.jar"
    local TESTS="${DIR}/.xcorpus/org.aspectj.matcher-1.6.9-tests.jar ${DIR}/.xcorpus/aspectjtools1.6.9-tests.jar ${DIR}/.xcorpus/aspectjweaver1.6.9-tests.jar ${DIR}/.xcorpus/aspectjrt1.6.9-tests.jar"
    local LIBS="${DIR}/.xcorpus/lib/avalon-framework-4.1.3.jar ${DIR}/.xcorpus/lib/commons-logging-1.1.1.jar ${DIR}/.xcorpus/lib/servlet-api-2.3.jar ${DIR}/.xcorpus/lib/commands-3.3.0-I20070605-0010.jar ${DIR}/.xcorpus/lib/text-3.3.0-v20070606-0010.jar ${DIR}/.xcorpus/lib/osgi-3.9.1-v20130814-1242.jar ${DIR}/.xcorpus/lib/common-3.6.200-v20130402-1505.jar ${DIR}/.xcorpus/lib/ant-launcher-1.8.1.jar ${DIR}/.xcorpus/lib/asm-3.2.jar ${DIR}/.xcorpus/lib/logkit-1.0.1.jar ${DIR}/.xcorpus/lib/ant-1.8.1.jar ${DIR}/.xcorpus/lib/log4j-1.2.12.jar ${DIR}/.xcorpus/build/main/aspectjtools1.6.9/ant_tasks/resources-ant.jar"
    local NATIVE_LIB="${XCORPUS_DIR}/data/qualitas_corpus_20130901/aspectj-1.6.9/native/x86_64-1.0.100-v20070510.jar"
    local BENCHMARK="aspectj-native"
    echo "./doop -i ${APP_INPUTS} ${TESTS} ${LIBS} ${NATIVE_LIB} -a ${ANALYSIS} --id ${BENCHMARK} --scan-native-code --discover-tests --timeout 240 |& tee ${CURRENT_DIR}/${BENCHMARK}.log"
}

if [ "${RUN_ANALYSIS}" == "1" ]; then
    # Generate java.hprof with "make capture_hprof".
    runDoop ${SERVER_ANALYSIS_TESTS}/009-native/build/libs/009-native.jar 009-native java_8 ${SERVER_ANALYSIS_TESTS}/009-native/java.hprof

    # Androidterm.
    runDoop ${DOOP_BENCHMARKS}/android-benchmarks/jackpal.androidterm-1.0.70-71-minAPI4.apk androidterm ${ANDROID_PLATFORM} ${DOOP_BENCHMARKS}/android-benchmarks/jackpal.androidterm.hprof.gz

    # Chrome.
    runDoop ${DOOP_BENCHMARKS}/android-benchmarks/com.android.chrome_57.0.2987.132-298713212_minAPI24_x86_nodpi_apkmirror.com.apk chrome ${ANDROID_PLATFORM} ${DOOP_BENCHMARKS}/android-benchmarks/com.android.chrome.hprof.gz

    # Instagram.
    runDoop ${DOOP_BENCHMARKS}/android-benchmarks/com.instagram.android_10.5.1-48243323_minAPI16_x86_nodpi_apkmirror.com.apk instagram ${ANDROID_PLATFORM} ${DOOP_BENCHMARKS}/android-benchmarks/com.instagram.android.hprof.gz

    # analyzeAspectJ
fi

printStatsTable ${BENCHMARK}
