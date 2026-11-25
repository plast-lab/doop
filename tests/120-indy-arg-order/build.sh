#!/bin/sh
# Rebuilds 120-indy-arg-order.jar from src/ (needs JDK 17+). The jar is committed: the test
# analyzes it as is. Entries are sorted and timestamps fixed, so a rebuild
# gives the same bytes.
set -e
cd "$(dirname "$0")"
rm -rf tmp && mkdir tmp
# jarof <dir> <jar> [manifest]: a jar of <dir>'s files, sorted, fixed dates
jarof() { ( cd "$1" && find . -type f | sed 's|^\./||' | LC_ALL=C sort > ../files.txt && jar --create --date=1980-02-01T00:00:02Z --file "$OLDPWD/$2" ${3:+--manifest "$OLDPWD/$3"} @../files.txt ) && rm -f "$1/../files.txt"; }
javac -g --release 8 -d tmp/classes src/main/java/*.java
printf 'Manifest-Version: 1.0\nMain-Class: Main\n' > tmp/main.mf
jarof tmp/classes 120-indy-arg-order.jar tmp/main.mf
rm -rf tmp
