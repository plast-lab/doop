#!/bin/sh
# Rebuilds 123-deployables.war and 123-deployables-boot.jar from src/ (needs JDK 17+). The jar is committed: the test
# analyzes it as is. Entries are sorted and timestamps fixed, so a rebuild
# gives the same bytes.
set -e
cd "$(dirname "$0")"
rm -rf tmp && mkdir tmp
# jarof <dir> <jar> [manifest]: a jar of <dir>'s files, sorted, fixed dates
jarof() { ( cd "$1" && find . -type f | sed 's|^\./||' | LC_ALL=C sort > ../files.txt && jar --create --date=1980-02-01T00:00:02Z --file "$OLDPWD/$2" ${3:+--manifest "$OLDPWD/$3"} @../files.txt ) && rm -f "$1/../files.txt"; }
javac -g --release 8 -d tmp/lib lib/src/main/java/lib/*.java
javac -g --release 8 -cp tmp/lib -d tmp/classes src/main/java/app/*.java
mkdir -p tmp/war/WEB-INF/classes tmp/war/WEB-INF/lib tmp/boot/BOOT-INF/classes tmp/boot/BOOT-INF/lib
jarof tmp/lib tmp/war/WEB-INF/lib/util.jar
cp tmp/war/WEB-INF/lib/util.jar tmp/boot/BOOT-INF/lib/util.jar
cp -r tmp/classes/. tmp/war/WEB-INF/classes/
cp -r tmp/classes/. tmp/boot/BOOT-INF/classes/
printf 'Manifest-Version: 1.0\nMain-Class: org.springframework.boot.loader.JarLauncher\nStart-Class: app.Hello\nSpring-Boot-Classes: BOOT-INF/classes/\nSpring-Boot-Lib: BOOT-INF/lib/\n' > tmp/boot.mf
jarof tmp/war 123-deployables.war
jarof tmp/boot 123-deployables-boot.jar tmp/boot.mf
rm -rf tmp
