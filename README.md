### Readme 

This is a temporary readme file for the jdoop project (doop with a Java driver). 

Currently, the project is a standalone Java application written in Groovy and managed by Gradle. 

Directory Structure
-------------------

The project contains the following directories:

* gradle: contains the gradle wrapper (gradlew) files, which allows us to build, run and deploy the project without installing Gradle manually.
* lib: the custom runtime dependencies of the project.
* logic: the logic files (placed here and not in src/dist to allow running the analyses without installing the app).
* src: the source files of the project.

It also contains the gradle build files (build.gradle and settings.gradle) and the gradle invocation scripts (gradlew and gradlew.bat).

Dependecies
-----------

Currently, the only requirement for either building or running jdoop is JDK 6 or higher. Neither Gradle nor Groovy is required to be installed manually.

Build
-----

Building the project refers to generating the runtime/distribution artifacts of jdoop.

To do so, we issue the following:

$ ./gradlew distZip

This builds the project and creates the jdoop distibution zip in the build/distributions directory.

We can also issue the following:

$ ./gradlew distTar

to create a tarball instead of a zip in the build/distributions directory.

To generate both (e.g. in order to release a new jdoop version and push it to the web site), we can issue:

$ ./gradlew distZip distTar

Install
-------

To install jdoop, we need to:

* extract the distribution zip or tarball in a directory of our choice.
* set the DOOP_HOME environment variable to point to the above directory.
* setup JRE 6 or higher.

Run
---

We can invoke jdoop by issuing:

$ DOOP_HOME>./bin/jdoop [OPTIONS] ANALYSIS JAR...

The jdoop command line arguments will be similar if not identical to the arguments of the original doop.

The jdoop application creates two directories in the DOOP_HOME:

* out: contains the analyses files (processed logic, jars, etc)
* logs: log files of the analyses, which are automatically recycled every day.

Local Install
-------------

Instead of generating the zip or tarball, we can instruct Gradle to install the application directly in our working directory:

$ ./gradlew installApp

This will create a build/install directory, containing all the jdoop runtime files (similar to generating the zip or tarball and extracting its files to the build/install directory).

Then we can switch to this directory, set the DOOP_HOME environment variable and invoke jdoop from there.

Run Directly
------------

This is the most convenient way to invoke jdoop. We issue:

$ ./gradlew run -Pargs="jdoop-command-line-arguments"

For example, the following invocation:

$ ./gradlew run -Pargs="context-insensitive ./lib/asm-debug-all-4.1.jar"

will run the context-insensitive analysis on the asm-debug-all-4.1 jar.

