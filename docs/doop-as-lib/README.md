This directory contains a test program that uses Doop as a library.

== Quick setup ==

Step 1. Build the example program:
```
./gradlew build
```

Step 2. Set environment variable `DOOP_HOME` to doop-home directory (see message printed by previous step).

Step 3. Run the example program via Gradle:
```
./gradlew run
```

== Development setup ==

Step 1. Build Doop using the local Maven repo:

```
cd $DOOP_HOME
./gradlew publishToMavenLocal
```

Step 2. Switch to this directory and uncomment the mavenLocal()
repository from build.gradle.

Step 3. Run the example program via Gradle:
```
./gradlew run
```
