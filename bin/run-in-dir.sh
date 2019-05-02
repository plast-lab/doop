#!/usr/bin/env bash

if [ "$1" == "" ] || [ "$2" == "" ]; then
    echo "Usage: run-in-dir.sh DIR CMD..."
    exit
fi

cd $1
shift 1
eval "$*"
