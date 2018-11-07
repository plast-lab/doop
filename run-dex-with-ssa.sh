#!/usr/bin/env bash

# Test script for running the dex front end with intermediate SSA
# transformation.

if [ "$1" == "" ]; then
    echo "Usage: run-dex-with-ssa.sh app"
    exit
else
    APP="$1"
    APP_NAME=$(basename "$1")
fi

FACTS_IN=$(realpath "${APP_NAME}-dex-facts")
FACTS_OUT=$(realpath "${APP_NAME}-dex-facts-ssa")
SSA_TANSFORMER=$(realpath souffle-scripts/ssa-transform.dl)

./doop -i ${APP} -a context-insensitive --id ${APP_NAME}-dex-facts --Xstop-at-facts ${FACTS_IN}

./gradlew souffleScript -Pargs="${SSA_TRANSFORMER} ${FACTS_IN} ${FACTS_OUT} ${DOOP_HOME}/cache 2 false false"

pushd ${FACTS_OUT}
echo "TODO: move"
exit
popd

./doop -i ${APP} -a context-insensitive --id ${APP_NAME}-analysis --Xstart-after-facts ${FACTS_OUT}
