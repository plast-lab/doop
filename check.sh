#!/usr/bin/env bash

IN=$ANDROID_IN
OUT=$ANDROID_OUT

echo ${IN}
echo ${OUT}

for file in ${IN}/*.facts; do
    file=$(basename "${file}")

    if [ ! -f "${OUT}/database/${file}" ]; then
        echo ${file}
    fi
done
