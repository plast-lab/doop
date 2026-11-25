#!/bin/sh
# Rebuilds 124-multi-release.jar from src/ (needs JDK 17+). The jar is committed: the test
# analyzes it as is. Entries are sorted and timestamps fixed, so a rebuild
# gives the same bytes.
set -e
cd "$(dirname "$0")"
rm -rf tmp && mkdir tmp
# jarof <dir> <jar> [manifest]: a jar of <dir>'s files, sorted, fixed dates
jarof() { ( cd "$1" && find . -type f | sed 's|^\./||' | LC_ALL=C sort > ../files.txt && jar --create --date=1980-02-01T00:00:02Z --file "$OLDPWD/$2" ${3:+--manifest "$OLDPWD/$3"} @../files.txt ) && rm -f "$1/../files.txt"; }
javac -g --release 8 -d tmp/classes src/main/java/*.java
javac -g --release 11 -d tmp/classes/META-INF/versions/11 src/main/java11/*.java
printf 'Manifest-Version: 1.0\nMain-Class: Main\nMulti-Release: true\n' > tmp/mr.mf
jarof tmp/classes 124-multi-release.jar tmp/mr.mf
rm -rf tmp
