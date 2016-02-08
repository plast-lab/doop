# Doop - Framework for Java Pointer Analysis

This document contains instructions for invoking the main driver of Doop. For a more detailed tutorial on using the results of Doop analyses, please consult [Doop-101](docs/doop-101.md). For an introduction to pointer analysis using Datalog, you can read a [research-level tutorial](http://yanniss.github.io/points-to-tutorial15.pdf).

## Getting Started

At its core, Doop is a collection of various analyses expressed in the form of Datalog rules--more specifically *LogiQL*, a Datalog dialect developed by [LogicBlox](http://www.logicblox.com/). As such, a core dependency is the commercial LogicBlox Datalog engine. You can [request an academic license for the LogicBlox engine](http://www.logicblox.com/learn/academic-license-request-form/). Currently, Doop supports versions 3.9 and 3.10.

For trouble-free configuration:

* The `LOGICBLOX_HOME` environment variable should point to the `logicblox` directory of the engine.
* The `DOOP_HOME` environment variable should point to the top-level directory of Doop.
* The `LB_PAGER_FORCE_START` environment variable should be set to true. (You will likely get an engine error about this, which becomes a warning with the flag. To eliminate, also set `LB_MEM_NOWARN` to true.)
* The `DOOP_JRE_LIB` environment variable should point to your JRE lib directory (see below--can be overridden with the `--jre-lib` option). JRE 6 or higher.
* The `DOOP_OUT` environment variable could point to the output files directory (optional, defaults to `$DOOP_HOME/out`).
* The `DOOP_CACHE` environment variable could point to the cached facts directory (optional, defaults to `$DOOP_HOME/cache`).

You can change [environment.sh](bin/environment.sh) accordingly for your system, and then source it (`source environment.sh`).


## Benchmarks & JRE Lib

For a variety of benchmarks, you could clone (or download) the [doop-benchmarks](https://bitbucket.org/yanniss/doop-benchmarks) repository.

One important directory in that repository is `JREs`. It can be used for the `DOOP_JRE_LIB` environment variable. It contains certain java library files for different JRE versions, necessary for analysis purposes. If you would like to provide a custom JRE lib directory (e.g., to run analyses using different minor versions), you should follow the same file structure. For example, in order to analyze with JRE version 1.6, you need a `jre1.6` directory containing at least `jce.jar`, `jsse.jar` and `rt.jar`.


## Running Doop

Doop only supports invocations from its home directory. The main options when running Doop are the analysis and the jar(s) options. For example, for a context-insensitive analysis on a jar file we issue:

    $ ./doop -a context-insensitive -j com.example.some.jar

### Common command line options
To see the list of available options (and valid argument values in certain cases), issue:

    $ ./doop -h

The options will be also shown if you run Doop without any arguments.

The major command line options are the following:

#### Analysis (-a, --analysis)
Mandatory. The name of the analysis to run.

Example:

    $ ./doop -a context-insensitive

#### Jar files  (-j, --jar)
Mandatory. The jar file(s) to analyse.

The jar option accepts multiple values and/or can be repeated multiple times.

The value of the Jar file can be specified in the following manners:

* provide the relative or absolute path to a local Jar file.
* provide the URL of a remote Jar file.
* provide the relative or absolute path to a local directory and all its \*.jar files will be included.
* provide a maven-style expression to indicate a Jar file from the Maven central repository.

Example:

```
#!bash
$ ./doop -j ./lib/asm-debug-all-4.1.jar      [local file]
		 -j org.apache.ivy:ivy:2.3.0         [maven descriptor]
		 -j ./lib                            [local directory]
		 -j http://www.example.com/some.jar  [remote file]
		 -j one.jar other.jar                [multiple files separated with a space]
```

#### JRE version (--jre)
The JRE version to use for the analysis. If *system* is used as the version, it shouldn't resolve to JRE 8.

Example:

    $ ./doop -a context-insensitive -j com.example.some.jar --jre 1.4

#### Main class (--main)
The main class to use as the entry point. This class must declare a method with signature `public static void main(String [])`. If not specified, Doop will try to infer this information from the manifest file of the provided jar file(s).

Example:

    $ ./doop -a context-insensitive -j com.example.some.jar --main com.example.some.Main

#### Timeout (-t, --timeout)
Specify the analysis execution timeout in minutes.

Example:

    $ ./doop -a context-insensitive -j com.example.some.jar -t 120

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

    $ ./doop -p /path/to/file.properties -a context-insensitive --jre 1.6

Please consult the [doop.properties](doop.properties) template file for more information.


## License
MIT license (see [LICENSE](LICENSE)).
