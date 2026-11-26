#!/bin/sh
# Rebuilds 130-mock-clinit.jar (application) and
# 130-mock-clinit-lib.jar (Spring stubs: just the annotations the analysis
# keys on) from src/ and lib/src/ (needs JDK 17+). The jars are committed: the
# test analyzes them as is. Entries are sorted and timestamps fixed, so a
# rebuild gives the same bytes.
set -e
cd "$(dirname "$0")"
rm -rf tmp && mkdir tmp
# jarof <dir> <jar> [manifest]: a jar of <dir>'s files, sorted, fixed dates
jarof() { ( cd "$1" && find . -type f | sed 's|^\./||' | LC_ALL=C sort > ../files.txt && jar --create --date=1980-02-01T00:00:02Z --file "$OLDPWD/$2" ${3:+--manifest "$OLDPWD/$3"} @../files.txt ) && rm -f "$1/../files.txt"; }
javac -g --release 8 -d tmp/lib $(find lib/src -name '*.java' | LC_ALL=C sort)
javac -g --release 8 -cp tmp/lib -d tmp/classes $(find src/main/java -name '*.java' | LC_ALL=C sort)
jarof tmp/lib 130-mock-clinit-lib.jar
jarof tmp/classes 130-mock-clinit.jar
rm -rf tmp
