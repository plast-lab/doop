# Doop - Framework for Java Pointer and Taint Analysis (using P/Taint)

This document contains instructions for invoking the main driver of Doop. For an introduction to Datalog, please consult [Datalog-101](docs/datalog-101.md). For a more detailed tutorial on using the results of Doop analyses, please consult [Doop-101](docs/doop-101.md). For an introduction to pointer analysis using Datalog, you can read a [research-level tutorial](http://yanniss.github.io/points-to-tutorial15.pdf). For information about Taint Analysis using Doop, please consult our [P/Taint paper](https://yanniss.github.io/ptaint-oopsla17.pdf), or [P/Taint tutorial](docs/ptaint.md).

## Getting Started

At its core, Doop is a collection of various analyses expressed in the form of Datalog rules. The framework has two versions of its rules:
one for [Soufflé](http://souffle-lang.org/), an open-source Datalog engine for program analysis (which is the default engine used), and another for **LogiQL**, a Datalog dialect developed by [LogicBlox](http://www.logicblox.com/). 
In order to install an up-to-date version of Soufflé, the best practice is to clone the development Github [repo](https://github.com/souffle-lang/souffle) and follow the instructions found on [this page](http://souffle-lang.org/docs/build/). 
For a LogicBlox engine, you can use **PA-Datalog**, a port available for academic use, by following the instructions found on [this page](http://snf-705535.vm.okeanos.grnet.gr/agreement.html). 

For trouble-free configuration:

* The `DOOP_PLATFORMS_LIB` environment variable could point to your PLATFORM lib directory (optional, see below).
* The `DOOP_OUT` environment variable could point to the output files directory (optional, defaults to `out`).
* The `DOOP_CACHE` environment variable could point to the cached facts directory (optional, defaults to `cache`).
* The `DOOP_LOG` environment variable could point to a log output directory (optional, defaults to `build/logs`).
* The `LOGICBLOX_HOME` environment variable should point to the `logicblox` directory of the engine, if you want to use LogicBlox.

There is currently no mailing list, but there is an alias for support questions: doop-support@googlegroups.com

## Benchmarks & Platform Lib

For a variety of benchmarks, you could clone (or download) the [doop-benchmarks](https://bitbucket.org/yanniss/doop-benchmarks) repository.

One important directory in that repository is `JREs`. It can be used for the `DOOP_PLATFORMS_LIB` environment variable. It contains certain java library files for different JRE versions, necessary for analysis purposes. If you would like to provide a custom DOOP_PLATFORMS_LIB directory (e.g., to run analyses using different minor versions), you should follow the same file structure. For example, in order to analyze with JRE version 1.6, you need a `jre1.6` directory containing at least `jce.jar`, `jsse.jar` and `rt.jar`. In order to run an an analysis on an android apk ideally you could create a link to your android sdk installation. The currently supported structure is Android/Sdk/.

## Running Doop

Doop only supports invocations from its home directory. The main options when running Doop are the analysis and the jar(s) options. For example, for a context-insensitive analysis on a jar file we issue:

    $ ./doop --platform java_8 -a context-insensitive -i com.example.some.jar

### Common command line options
To see the list of available options (and valid argument values in certain cases), issue:

    $ ./doop -h

The options will be also shown if you run Doop without any arguments.

The major command line options are the following:

#### Analysis (-a, --analysis)
Mandatory. The name of the analysis to run.

Example:

    $ ./doop -a context-insensitive

#### Input files  (-i, --inputs)
Mandatory. The input file(s) to analyse.

The inputs option accepts multiple values and/or can be repeated multiple times.

The value of the input file can be specified in the following manners:

* provide the relative or absolute path to a local input file.
* provide the URL of a remote input file.
* provide the relative or absolute path to a local directory and all its \*.jar files will be included.
* provide a maven-style expression to indicate a Jar file from the Maven central repository.

Example:

```
#!bash
$ ./doop -i ./lib/asm-debug-all-4.1.jar      [local file]
		 -i org.apache.ivy:ivy:2.3.0         [maven descriptor]
		 -i ./lib                            [local directory]
		 -i http://www.example.com/some.jar  [remote file]
		 -i one.jar other.jar                [multiple files separated with a space]
```

#### PLATFORM (--platform)
Optional --- default: java_8. The platform to use for the analysis. The possible Java options are java_N where N is the java version (3, 4, 5, 6, 7, 8 etc.). The android options are android_N_V where N is the Android version (20, 21, 22, 23, 24, 25 etc.) and V is the variant ("stubs" for the Android SDK libraries or "fulljars" for custom built platforms).

Example:

    $ ./doop -a context-insensitive -i com.example.some.jar --platform java_4
    $ ./doop -a context-insensitive -i some-app.apk --platform android_24

#### Main class (--main)
The main class to use as the entry point. This class must declare a method with signature `public static void main(String [])`. If not specified, Doop will try to infer this information from the manifest file of the provided jar file(s).

Example:

    $ ./doop -a context-insensitive -i com.example.some.jar --main com.example.some.Main

#### Timeout (-t, --timeout)
Specify the analysis execution timeout in minutes.

Example:

    $ ./doop -a context-insensitive -i com.example.some.jar -t 120

The above analysis will run for a maximum of 2 hours (120 minutes).

#### Analysis id (-id, --identifier)
The identifier of the analysis.

If the identifier is not specified, Doop will generate one automatically. Use this option if you prefer
to provide a human-friendly identifier to your analysis.

Example:

    $ ./doop -id myAnalysis

#### Packages (--regex)
The Java packages to treat as application code (not library code), to be exhaustively analyzed.

Example:

    $ ./doop --regex com.example.package1.*:com.example.package2.*

#### Properties file (-p, --properties)
You can specify the options of the analysis in a properties file and use the `-p` option
to process this file, as follows:

    $ ./doop -p /path/to/file.properties

You can also override the options from a properties file with options from the command line. For example:

    $ ./doop -p /path/to/file.properties -a context-insensitive --platform java_6

#### Native code scanner (--scan-native-code)
This option makes Doop scan the native dynamic libraries bundled in .jar or .apk inputs, to find possible calls from JNI code to Java code.

* For Java programs, this functionality assumes that standard command-line tools are available (such as `nm`, `objdump`, or `strings`).
* For Android programs containing ARM native code, at least one the following environment variables must be set: `ARMEABI_TOOLCHAIN`/`AARCH64_TOOLCHAIN`. Each such variable points to the corresponding architecture directory inside the toolchain of an Android NDK installation, such as `/path/to/toolchain/arm-linux-androideabi`. Consult Android documentation for generating such a [standalone toolchain](https://developer.android.com/ndk/guides/standalone_toolchain).

### Soufflé multithreading

Soufflé supports multithreading so you can select the number of threads the analysis will run on by providing the --souffle-jobs argument to doop. For example:

    $ ./doop -i ../doop-benchmarks/dacapo-2006/antlr.jar -a context-insensitive --platform java_7 --dacapo --id souffle-antlr --souffle-jobs 12

### Soufflé profile

You can then inspect the analysis results by using the souffle-profile command and providing the profile.txt file produced by Souffle under the output directory of the analysis. In order to inspect the profile.txt of the above doop invocation with --souffle you would use the following command:

    $ souffle-profile out/context-insensitive/souffle-antlr/profile.txt

### Using LogicBlox as the Datalog engine of choice

In order to use LogicBlox instead of the Soufflé engine you can provide the --lb argument. 

    $ ./doop -i ../doop-benchmarks/dacapo-2006/antlr.jar -a context-insensitive --platform java_7 --dacapo --id lb-antlr --lb

### Running Doop in offline mode

Normally, on each invocation of Doop the underlying build system will check for newer versions of all dependency libraries.
Sometimes, it might be desirable to invoke doop in an offline mode. There is an alternative script for this purpose.

    $ ./doopOffline --platform java_8 -a context-insensitive -i com.example.some.jar

### Building Doop distribution

Optionally, Doop can be built as a binary distribution with the
following command:

```
#!bash
$ ./gradlew distZip                  # or distTar
```

The resulting distribution archive can be found under
build/distributions and can be decompressed to a directory. Doop is
invoked in that directory with "./bin/doop" instead of "./doop",
bypassing Gradle (and its dependency resolution) on each Doop
run. This can help with dependency resolution issues due to network
connectivity or to avoid Gradle overhead when running Doop in batch
mode.

## License
UPL (see [LICENSE](LICENSE)).


## Development on Doop
The `doop` command is a script for Gradle build tasks. If you want to see all available tasks (e.g., how to build stand-alone packages of Doop for offline use), try `./gradlew tasks`. Generally, for development and integration instructions, please consult the [Doop Developer Guide](docs/documentation.md).
