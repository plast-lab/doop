#!/usr/bin/env bash
#
# This script compares (via diff) the logic/facts of two different analysis runs.
# This can be used to debug any analysis non-determinism (issue #49).

function diffFiles() {
    local SORTED=$1
    local FILE1=$2
    local FILE2=$3
    # echo "Comparing: ${FILE1} ${FILE2}"
    if [ "${SORTED}" == "true" ]; then
        local FILES='<(sort -u '${FILE1}') <(sort -u '${FILE2}')'
    else
        local FILES="${FILE1} ${FILE2}"
    fi
    DIFF_RES=$(eval "diff ${FILES}")
    if [ "${DIFF_RES}" != "" ]; then
        echo "Files differ: ${FILE1} ${FILE2}"
        echo "Inspect with: vimdiff ${FILES}"
        # echo "${DIFF_RES}"
    fi
}

function diffFilesWithCut() {
    local CUT_SPEC="$1"
    local FACT_FILE="$2"
    echo "[*] Comparing facts: ${FACT_FILE}..."
    cut -f ${CUT_SPEC} ${DB1}/${FACT_FILE} > ${DB1}/${FACT_FILE}.1
    cut -f ${CUT_SPEC} ${DB2}/${FACT_FILE} > ${DB2}/${FACT_FILE}.1
    diffFiles true ${DB1}/${FACT_FILE}.1 ${DB2}/${FACT_FILE}.1
}

if [ "$1" == "" ] || [ "$2" == "" ]; then
    echo "Usage: diff-analyses.sh DIR1 DIR2"
    echo ""
    echo "Compares two finished Doop analyses. Takes two directories of the form 'out/<ANALYSIS_ID>'."
    exit
fi

DIR1="$1"
DIR2="$2"

echo "[*] Comparing 'meta' analysis configurations..."
diffFiles false ${DIR1}/meta ${DIR2}/meta

echo "[*] Comparing logic..."
LOGIC_FILES=$(ls ${DIR1}/gen_*.dl ${DIR2}/gen_*.dl | xargs)
diffFiles false ${LOGIC_FILES}

DB1="${DIR1}/database"
pushd ${DB1} &> /dev/null
FACT_FILES_1=$(ls *.facts | xargs)
popd &> /dev/null

DB2="${DIR2}/database"
pushd ${DB2} &> /dev/null
FACT_FILES_2=$(ls *.facts | xargs)
popd &> /dev/null

FACT_FILES=$(echo "${FACT_FILES_1} ${FACT_FILES_2}" | sed -e 's/ /\n/g' | sort -u | xargs)
for FACT_FILE in ${FACT_FILES}; do
    if [ "${FACT_FILE}" == "XMLNode.facts" ] || [ "${FACT_FILE}" == "XMLNodeAttribute.facts" ]; then
        continue
    fi
    echo "[*] Comparing facts: ${FACT_FILE}..."
    diffFiles true ${DB1}/${FACT_FILE} ${DB2}/${FACT_FILE}
done

diffFilesWithCut '2-' XMLNode.facts
diffFilesWithCut '2-' XMLNodeAttribute.facts
