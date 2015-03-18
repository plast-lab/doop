# Doop - Framework for Java Pointer Analysis

This is the readme file for the Doop project. For more information, please consult the
`documentation.md` file.

## Directory Structure

The project contains the following directories:

* *gradle*: contains the gradle wrapper (gradlew) files, which allows us to build, run and deploy the project without installing Gradle manually.
* *lib*: the custom runtime dependencies of the project.
* *logic*: the logic files (placed here and not in src/dist to allow running the analyses without installing the app).
* *src*: the Java/Groovy source files of the project.

It also contains the gradle build files (build.gradle and settings.gradle) and the gradle invocation scripts (gradlew and gradlew.bat).

## Building Doop

Building the Doop project refers to generating its runtime/distribution artifacts.

To do so, we issue the following:

    $ ./gradlew distZip

This builds the project and creates the Doop distibution zip in the build/distributions directory.

We can also issue the following:

    $ ./gradlew distTar

to create a tarball instead of a zip in the build/distributions directory.

To generate both, we can issue:

    $ ./gradlew distZip distTar

## Installing Doop

To install Doop, we need to:

* extract the distribution zip or tarball in a directory of our choice.
* set the `$DOOP_HOME` environment variable to point to the above directory.
* install the LogicBlox engine and set the environment variable `$LOGICBLOX_HOME` or use the `-lbhome` flag
  upon each Doop invocation. Doop will take care of setting additional environment variables, such as `$PATH` and `$LD_LIBRARY_PATH`.
* setup JRE 6 or higher. Neither Gradle nor Groovy is required to be installed manually.

## Running Doop

We can invoke Doop by issuing:

    $ DOOP_HOME>./bin/doop [OPTIONS]...

If you are familiar with the old Doop run scripts, the major difference is that the
analysis and the jar(s) are treated as options and not as arguments. For example, the old Doop run script invocation:

    $ >./run context-insensitive ./lib/asm-debug-all-4.1.jar

should be given as follows in the new Java-based Doop:

    $ DOOP_HOME>./bin/doop -a context-insensitive -j ./lib/asm-debug-all-4.1.jar

### Command line options
To see the list of available option, issue:

    $ DOOP_HOME>./bin/doop -h

The options will be also shown if you run Doop without any arguments.

The major command line options of Doop are the following:

#### Analysis (-a, --analysis)
The name of the analysis to run.

Example:

    $ DOOP_HOME>./bin/doop -a context-insensitive

To see the list of available analyses run Doop with the `-h` flag.

The analysis option is mandatory.

#### Jar files  (-j, --jar)
The jar file(s) to analyse.

The jar option accepts multiple values and/or can be repeated multiple times.

The value of the Jar file can be specified in the following manners:

* provide the relative or absolute path to a local Jar file.
* provide the URL of a remote Jar file.
* provide the relative or absolute path to a local directory and all its \*.jar files will be included.
* provide a maven-style expression to indicate a Jar file from the Maven central repository.

Example:

    $ DOOP_HOME>./bin/doop -j ./lib/asm-debug-all-4.1.jar      [local file]
                           -j org.apache.ivy:ivy:2.3.0         [maven descriptor]
                           -j ./lib                            [local directory]
                           -j http://www.example.com/some.jar  [remote file]
                           -j one.jar,other.jar                [multiple files separated with a comma]

The jar option is mandatory.

#### Packages (--regex)
The Java packages to analyse.

    $ DOOP_HOME>./bin/doop --regex com.example.package1.*:com.example.package2.*


#### Main class (--main)
The main class to use as the entry point.

Example:

    $ DOOP_HOME>./bin/doop -a context-insensitive -j com.example.some.jar --main com.example.some.Main


#### JRE version (--jre)
The JRE version to use for the analysis.

Example:

    $ DOOP_HOME>./bin/doop -a context-insensitive -j com.example.some.jar --main com.example.some.Main --jre 1.4

To see the list of supported JRE versions run Doop with the `-h` flag.


#### LogicBlox home (--lbhome)
The path of the LogicBlox installation directory.

By default, Doop uses the value of the `$LOGICBLOX_HOME` environment variable as the location of the LogicBlox
installation directory. You may use the `--lbhome` option if you want to run the specific analysis via a different
LogicBlox version.


#### Timeout (-t, --timeout)
Specify the analysis execution timeout in minutes.

Example:

    $ DOOP_HOME>./bin/doop -a context-insensitive -j com.example.some.jar -t 120

The above analysis will run for a maximum of 2 hours (120 minutes).

#### Using a properties file to specify the analysis options (-p, --properties)
You can specify the options of the analysis in a properties file and use the `-p` option
to process this file, as follows:

    $ DOOP_HOME>./bin/doop -p /path/to/file.properties

When the `-p` (or `--properties`) option is given, all other options are ignored.
Please consult the `doop.properties` file which offers a skeleton of the properties file
supported by doop.

To create the `doop.properties` file you should issue the following:

    $ ./gradlew createProperties


### Directories created

Doop creates the following directories in the DOOP_HOME:

* out: contains the analyses files (processed logic, jars, LogicBlox workspace, etc).
* results: contains symlinks to the analyses files.
* logs: log files of the analyses, which are automatically recycled every day.

## Local Install

Instead of generating the zip or tarball, we can instruct Gradle to install the Doop app directly in the development
directory:

    $ ./gradlew installApp

This will create a build/install directory, containing all the Doop runtime files
(similar to generating the zip or tarball and extracting its files to the build/install directory).

Then we can switch to this directory, set the DOOP_HOME environment variable and invoke doop from there.

## Runnning Doop Directly

When developing Doop, a convenient way to invoke it directly is by issuing:

    $ ./gradlew run -Pargs="doop-command-line-arguments"

For example, the following invocation:

    $ ./gradlew run -Pargs="-a context-insensitive -j ./lib/asm-debug-all-4.1.jar"

will run the context-insensitive analysis on the asm-debug-all-4.1 jar.

The most convenient way is to use the `doop` bash script which runs gradlew for you:

    $ ./doop -a context-insensitive -j ./lib/asm-debug-all-4.1.jar

## License
MIT license (see `LICENSE`).