#!/usr/bin/env bash

# Test script for running the dex front end with intermediate SSA
# transformation.

if [ "$1" == "" ]; then
    echo "Usage: run-dex-with-ssa.sh app"
    exit
else
    APP="$1"
    APP_NAME=$(basename "$1" | sed -e 's/[()]/_/g')
fi

FACTS_IN=${ANDROID_IN}
FACTS_OUT=${ANDROID_OUT}
SSA_TRANSFORMER=$(realpath souffle-scripts/ssa-transform.dl)

./doop -i ${APP} -a context-insensitive --id ${APP_NAME}-dex-facts --platform android_25_fulljars --dex --Xstop-at-facts ${FACTS_IN} --cache

./gradlew souffleScript -Pargs="${SSA_TRANSFORMER} ${FACTS_IN} ${FACTS_OUT} ${DOOP_HOME}/cache 26 true false false false"

pushd "${FACTS_OUT}/database"
echo "TODO: move"

for file in ${FACTS_IN}/*.facts; do
    filename=$(basename "${file}")

    if [ ! -f "${filename}" ]; then
        cp $file .
    fi
done

popd

./doop -i ${APP} -a context-insensitive --id ${APP_NAME}-analysis --platform android_25_fulljars --Xfacts-subset PLATFORM --Xuse-existing-facts ${FACTS_OUT}/database
