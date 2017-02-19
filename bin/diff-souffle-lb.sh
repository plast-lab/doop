#!/bin/bash

# Script to diff two files that contain the relation sizes of an analysis that
# was run under Souffle-Doop and LogicBLox-Doop.

# Run as: diff-souffle-lb SOUFFLE_FILE LOGICBLOX_FILE [-k] (order matters)
#
# SOUFFLE_FILE    output of 'souffle-profile SOUFFLE_PROF_INFO_FILE -c rel'
# LOGIXBLOX_FILE  output of 'bloxbatch -db LB_DB -popCount'
# -k              keep temporary files

SouffleFile=${1?missing param: Souffle popCount file}
LogicBloxFile=${2?missing param: LogicBlox popCount file}

if [ ! -f "$SouffleFile" ]; then
	echo "$0: Souffle popCount file not found!"
	exit 1
fi

if [ ! -f "$LogicBloxFile" ]; then
	echo "$0: Logicblox popCount file not found!"
	exit 1
fi

SouffleTempFile="$SouffleFile.temp$$"
LogicBloxTempFile="$LogicBloxFile.temp$$"

tail -n +4 $SouffleFile | sed '$d' | awk '{print $7 ": "  $5}' > $SouffleTempFile
sed -i -e 's/^is//' $SouffleTempFile
sed -i -e 's/_/:/g' $SouffleTempFile
sort $SouffleTempFile -o $SouffleTempFile

awk -F' ' 'NR==FNR{c[$1]++;next};c[$1] > 0' $SouffleTempFile $LogicBloxFile | sort -o $LogicBloxTempFile
awk -F' ' 'NR==FNR{c[$1]++;next};c[$1] > 0' $LogicBloxTempFile $SouffleTempFile | sort -o $SouffleTempFile
colordiff -u $SouffleTempFile $LogicBloxTempFile

if [ "$3" != "-k" ]; then
	rm $SouffleTempFile
	rm $LogicBloxTempFile
fi
