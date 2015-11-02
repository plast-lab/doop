#!/bin/bash
! [[ $# = 1 ]] && { echo "MUST GIVE EXACTLY ONE ARGUMENT (the jar file)"; exit 1; }

JAR=$1
APPREGEX=$(jar tf $JAR | grep .class | sed -r 's/.class$//' | tr '\n' ':')
GLOBIGNORE="lib/sootclasses-2.5.0-custom.jar"
CLASSPATH=build/classes/main/:$(ls lib/*jar | tr '\n' ':'):$(ls ~/.gradle/wrapper/dists/gradle-2.4-bin/*/gradle-2.4/lib/*jar | tr '\n' ':')

rm -rf facts database
java -cp $CLASSPATH doop.soot.Main -full -keep-line-number -use-original-names -only-application-classes-fact-gen -lsystem -application-regex $APPREGEX -allow-phantom -d facts $JAR
