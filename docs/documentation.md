# Doop Developer Guide

This document[^about] describes the design, implementation, and usage of the Java-based Doop scaffolding.

## Contents
* [Overview](#overview)
* [Building Doop](#building)
    * [Clone the Doop repository](#building.cloning)
    * [Directory Structure](#building.structure)
    * [Using Gradle to Execute the Build Tasks](#building.gradle)
    * [The Gradle build script](#building.script)
* [Running Doop](#running)
    * [Main options](#running.main)
    * [Analyze the Dacapo Benchmarks suite](#dacapo)
* [Design and Implementation](#design)
    * [Goals](#design.goals)
    * [The classes of the Core API](#design.api)
    * [The classes of the Doop CLI](#design.cli)


## Overview {#overview}
Doop is a framework for Java pointer analysis. It is implemented in Datalog, Java and [Groovy](http://www.groovy-lang.org) providing:

* A Java-based API for running points-to analyses for Java programs (Doop Core API).
* A standalone application for running the analyses through a command-line interface (Doop CLI).

Doop uses [Gradle](http://www.gradle.org) as its build system.

Doop uses [Souffle](https://souffle-lang.github.io) as its Datalog
engine. Previous versions used
[LogicBlox](http://www.logicblox.com/) by default but this logic is
currently deprecated.

## Building Doop {#building}
This section describes the process of building Doop and its various options.

### Clone the Doop repository {#building.cloning}
Clone the Doop repo from [bitbucket](http://bibucket.org):

    git clone https://bitbucket.org/yanniss/doop

### Directory Structure {#building.structure}
The directory structure of the repository follows the established conventions for Java projects. Specifically,
the repository contains the following directories:

* *docs*: various documentation files with more in-depth information and elaborate examples.
* *gradle*: contains the Gradle wrapper (gradlew) files [^building.gradlew].
* *souffle-logic*: the Souffle-based analysis logic.
* *src`*: the code that drives the Doop pipeline (process analysis
  inputs, generate facts, compile Datalog logic, invoke logic on input
  facts to generate output facts).
* *generators*: standalone code generators/processors:
  - *generators/soot-fact-generator*: the
	[Soot](https://github.com/soot-oss/soot/)-based fact generator.
  - *generators/wala-fact-generator*: The
    [WALA](http://wala.sourceforge.net/wiki/index.php/Main_Page)-based
    fact generator.
  - *generators/dex-fact-generator*: An experimental fact generator for
    Dalvik executable code.
  - *generators/code-processor*: Functionality for processing Jimple (in
    text representation) and generating results in SARIF format.

For legacy reasons, directory *lb-logic* contains old/deprecated Logicblox-based analysis logic.

The source directory is structured according to the Gradle conventions
[Java](http://gradle.org/docs/current/userguide/java_plugin.html),
[Groovy](http://gradle.org/docs/current/userguide/groovy_plugin.html) and
[Application](http://gradle.org/docs/current/userguide/application_plugin.html)
plugins, containing the following sub-directories[^building.logic]:

* *main/antlr*: ANTLR grammars.
* *main/groovy*: Groovy sources (Core API, CLI, code processing).
* *main/java*: Java sources (soot fact generation).
* *main/resources*: default Log4j properties (and any other Java-based resource files).

This repository also contains:

* the Gradle build scripts (build.gradle and settings.gradle),
* the Gradle wrapper invocation scripts (gradlew and gradlew.bat),
* the .hgignore file.
* the README file and this documentation.

Doop creates the following directories under DOOP_HOME:

* *build*: compile-time produced files (class files).
* *out*: runtime produced files (processed logic, analysis workspace, etc).
* *results*: symlinks to the analyses files.
* *logs*: log files of the analyses, which are automatically recycled every day.

### Using Gradle to Execute the Build Tasks {#building.gradle}
After cloning the repo, we can execute the build task of choice by issuing the following:

    $ ./gradlew [name-of-task]

To list the build tasks supported, we can issue:

    $ ./gradlew tasks

The main tasks available are the following:

* *classes* - Assembles the Java and Groovy classes.
* *jar* - Assembles a jar archive containing the classes.
* *distTar* - Bundles the project as a tar archive. The file is the distribution artifact of the Doop analysis
framework, as it offers Doop as a self-contained, standalone Java application with libs and automatically-generated
OS specific scripts. This task should be used for generating the Doop distro to publish to a website.
* *distZip* - As above, but generates a zip archive.
* *installApp* - Installs Doop to the build/install directory (identical to producing distTar
or distZip and unpacking the contents to the build/install directory).
* *run* - Runs the Doop CLI directly (without installing it).
* *clean* - Deletes the build directory (generated by Gradle for storing the output of the build tasks).
* *javadoc* - Generates Javadoc API documentation for the Java source code.
* *groovydoc* - Generates Groovydoc API documentation for the Groovy source code.
* *createProperties* - Creates the skeleton of a doop properties file.

### The Gradle build script {#building.script}
The Gradle build script (build.gradle) contains the settings and code required to execute the build tasks.
The script uses Gradle's Groovy-based DSL to:

* apply the Groovy and Application plugins,
* set the source and target compatibility for the generated class files (currently, it has to be 1.6),
* define the name of the Main class (for the CLI),
* setup the jar repositories,
* define the project's compile-time and run-time dependencies,
* define the custom createProperties task,
* customize the files to be included in the application distribution,
* configure environment variables and system settings for running the CLI directly.

## Running Doop {#running}
This section describes the various command line options supported for running Doop.

### Main options {#running.main}
To list all the available options run Doop without any parameters (or
with the -h flag). Since command-line options are numerous, Doop
provides a help system with options split in sections: run `--help` to
see the available sections, `--help <SECTION>` to see the commands in
a specific secttion, or `--help all` to see all Doop options.

The main command line options are described in the `README.md` file:

* -a, --analysis: The name of the analysis.
* -i, --input: The jar file(s) to analyse.
* --platform: The platform and version to use. Doop checks for the JRE-specific or Android-specific files
         in the `$DOOP_PLATFORMS_LIB` directory. For instance, `java_7` specifies the Java platform and that
		 JRE 1.7 will be used to resolve library calls, while `android_25_fulljars` specifies the Android
		 platform and the Android library version 25 will be used to resolve calls to the Android library).
* --main: The name of the Java main class.
* -t, --timeout: The analysis execution timeout in minutes.
* -id, --identifier: The human-friendly identifier of the analysis (if not specified, Doop will generate one automatically).
* --regex: The Java package names to analyse.
* -p, --properties: Load options from the given properties file.

### Analyze the Dacapo Benchmarks suite {#dacapo}

Doop provides special handling of the [DaCapo Benchmarks suite](http://dacapobench.org/). You can check the `README` file for
more information on how to acquire those benchmarks.

For example, in order to analyze a DaCapo 2006 benchmark we could issue the following:

    $ ./doop -a context-insensitive -i benchmarks/dacapo-2006/antlr.jar --dacapo

Respectively, for a DaCapo Bach benchmark we could issue the following:

    $ ./doop -a context-insensitive -i benchmarks/dacapo-bach/avrora/avrora.jar --dacapo-bach


## Design and Implementation {#design}

### Goals {#design.goals}
The primary goals of the Java/Groovy part of Doop are the following:

1. Offer an embeddable and multi-tenant Java/Groovy API for running the analyses.
1. Develop a unified code-base that is highly maintainable, flexible and extensible.

### The classes of the Core API {#design.api}
The core API is contained in the org.clyze.doop.core Groovy package and contains the following classes.

#### AnalysisFactory
A Factory for creating analyses. The class provides the following public methods:

    newAnalysis(String id, String name, Map<String, AnalysisOption<?>> options, List<String> jars)
    
    newAnalysis(String id, String name, Map<String, AnalysisOption<?>> options, InputResolutionContext ctx)

which create a new `Analysis` object. The methods check and verify that all provided information (id and name
of the analysis, jar files and options) is correct, throwing an exception in case of error. The checks performed are
based on the doop run script and are implemented using private or protected methods.

This class is extended by `CommandLineAnalysisFactory` to support creating `Analysis` objects from the
CLI.

#### Analysis
An object that holds both:

1. the analysis options and inputs
2. the code to execute the individual phases/steps of the analysis.

The class implements the `Runnable` interface to support running analyses in separate threads. To this end, the public
method:

    void run()

provides the entry point for starting the execution of an analysis.

The class also provides the following public methods:

* `printStats()` - print the statistics of the analysis (a la doop run script).
* `linResult()` - links the results of the analysis (a la doop run script).
* `query(String query, Closure closure)` - executes the given bloxbatch query (using the -query flag), providing
each line of output to the given closure.
* `toString()` - returns a representation of the analysis as a String (used for debugging).

All the other methods of the class are either private or protected, as they are used to implement "internal details"
of executing an analysis.

#### AnalysisOption
A class that models an analysis option. Each option contains the following attributes:

* *id*: The identifier of the option (as used internally by the code, the preprocessors, etc.).
* *name*: The name of the option (as presented to the end-user).
* *optName*: The shorthand name of the option (for usage in cli).
* *description*: The description of the option (which is also presented to the end-user).
* *value*: The value of the option.
* *validValues*: Optional set of valid values.
* *multipleValues*: Boolean flag indicating that the option accepts multiple values.
* *argName* - The description of the option's value which will be displayed to the end-user. All String options should
              define an argName.
* *argInputType*: The InputType for options that have a file as argument.
* *cli*: Boolean flag indicating whether the option should be included in the CLI.
* *isMandatory*: Boolean flag indicating whether the options is mandatory or not.
* *forCacheID*: Boolean flag indicating whether the option should affect the computation of the cache ID.
* *forPreprocessor*: Boolean flag indicating whether the option is used by the preprocessor.
* *changesFacts*: Boolean flag indicating whether the option may affect the generated facts.

The use of this class allows us to significantly simplify and reduce the code required to process and manage the
analysis options in the various Doop usage scenarios.

#### Doop
The low-level initialization point of the Java/Groovy part of the framework.

This class provides the following public method:

    void initDoop(String homePath, String outPath)

which sets the two main paths for each Doop deployment:

* the doop home path, which determines the location of the logic files,
* the doop output path, which determines the location of the *out* directory generated by the framework.

The class also holds a list of all the available Analysis options (in the `ANALYSIS_OPTIONS` final field).

Finally, the `Doop` class provides the following helper methods for initializing the analysis options:

* `createDefaultAnalysisOptions()`: obtains a `Map<String, AnalysisOption<?>>` using
the default values defined in the `ANALYSIS_OPTIONS` list.
* `overrideDefaultOptionsWithCLI(cli, filter)`: overrides the default options with the values contained in the CLI.
* `overrideDefaultOptionsWithPropertiesAndCLI(properties, cli, filter)`: overrides the default options with the values contained in the properties and the CLI.
  An option in the CLI superseeds a property one.

The filter accepted by the last two methods is a closure that filters the options that should be set. For
 example, the following invocation:
  
    Doop.overrideDefaultOptionsWithPropertiesAndCLI(properties, cli) { AnalysisOption option ->
        option.cli == true
    }
    
overrides only the options that have their `cli` boolean flag set to true.

#### Helper
A class that holds various helper methods for logging, executing external processes, working with files, etc.

#### org.clyze.doop.core.ImportGenerator
The fact declarations import generator. Mimics the behavior of the writeImport bash script.

#### Analysis inputs - the `org.clyze.doop.input` package
The `org.clyze.doop.input` package contains a simple mechanism for dealing with the various analysis inputs and
dependencies (local jar files, local directories, remote jar URLs and jars held in the Maven Central repository).
The mechanism is based on the `Input`, `InputResolver` and `InputResolutionContext`
interfaces. The first holds a mapping from a String value to a set of local files, the second offers a
mechanism to construct this mapping in an `InputResolutionContext` which ultimately holds the whole set of mappings
between inputs and files.

#### Adding a new analysis option
To add a new analysis option to the framework, we need to:

* define the option in the `ANALYSIS_OPTIONS` list. For String options, it is currently necessary to define the
argName of the option.
* implement the validation/checks required for the new option (if any) in the `AnalysisFactory`,
* update the implementation of `Analysis` to take into account the new option during the execution of the analysis phases.


### The classes of the Doop CLI {#design.cli}

#### Main
The entry point of the Doop framework. The `Main` class collects the command-line options supplied by the
user and starts the execution of the analysis.

#### CommandLineAnalysisFactory
The class extends `AnalysisFactory` to enable the creation of a `Analysis` object from the command
line arguments and/or a properties file.

[^about]: This document is to be used with [Pandoc](http://johnmacfarlane.net/pandoc/), using an invocation like the
following:

    pandoc -f markdown -t html -s -o outfile infile

[^building.gradlew]: Using the [Gradle wrapper](https://gradle.org/docs/current/userguide/gradle_wrapper.html) is the
suggested way to run Gradle, allowing us to build, run or deploy the project without installing Gradle manually (the
wrapper downloads Gradle for us).

[^building.logic]: Although we use Gradle's Application plugin, the logic files are placed in the *logic* top-level
directory and not in the *src/dist* sub-directory (which is the standard Gradle convention for placing files to be
distributed along with the application). This helps us support running the analyses without installing the Doop app.
