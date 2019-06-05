#!/usr/bin/env bash

if [ "$1" == "" ] || [ "$2" == "" ] || [ "$3" == "" ]; then
    echo "Usage: run-with-redirection.sh INPUT OUTPUT CMD..."
    exit
fi

INPUT=$1
OUTPUT=$2
shift 2
eval "$*" < ${INPUT} &> ${OUTPUT}
