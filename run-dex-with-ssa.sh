#!/usr/bin/env bash

# Test script for running the dex front end with intermediate SSA
# transformation.

if [ "$1" == "" ] || [ "$2" == "" ]; then
    echo "Usage: run-dex-with-ssa.sh <apk> <final-facts-out>"
    exit
else
    APP="$1"
    APP_NAME=$(basename "$1" | sed -e 's/[()]/_/g')
fi

ID=${APP_NAME}-dex-facts
FACTS_IN=${ID}
FACTS_OUT=$(realpath $2)
SSA_TRANSFORMER=$(realpath souffle-scripts/ssa-transform.dl)

echo "APP_NAME=${APP_NAME}"

./doop -i ${APP} -a context-insensitive --id ${ID} --platform android_25_fulljars --dex --Xfacts-subset APP --Xstop-at-facts ${FACTS_IN} --cache

./gradlew souffleScript -Pargs="${SSA_TRANSFORMER} ${FACTS_IN} ${FACTS_OUT} ${DOOP_HOME}/cache 26 false false false false false"

pushd "${FACTS_OUT}/database"

for file in ${FACTS_IN}/*.facts; do
    filename=$(basename "${file}")

    if [ ! -f "${filename}" ]; then
        cp $file .
    fi
done

popd

exit 1

./doop -i ${APP} -a context-insensitive --id ${APP_NAME}-analysis --platform android_25_fulljars --Xfacts-subset PLATFORM --Xuse-existing-facts ${FACTS_OUT}/database
