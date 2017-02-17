#!/bin/bash

SouffleLog=$1
LogicBloxLog=$2

SouffleTempLog="$SouffleLog.temp"
LogicBloxTempLog="$LogicBloxLog.temp"

echo $SouffleLog
echo $SouffleTempLog

tail -n +4 $SouffleLog | sed '$d' | awk '{print $7 ": "  $5}' > $SouffleTempLog
sed -i -e 's/^is//' $SouffleTempLog
sed -i -e 's/_/:/g' $SouffleTempLog
sort $SouffleTempLog -o $SouffleTempLog

awk -F' ' 'NR==FNR{c[$1]++;next};c[$1] > 0' $SouffleTempLog $LogicBloxLog | sort > $LogicBloxTempLog
colordiff -u $SouffleTempLog $LogicBloxTempLog

rm $SouffleTempLog
rm $LogicBloxTempLog
