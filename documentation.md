# JDoop Documenation

This document[^about] describes the design, implementation and usage of the new Java-based Doop (JDoop - Doop with a Java driver).

## Contents
* [Overview](#overview)
* [Building JDoop](#building)
    * [Clone the JDoop repository](#building.cloning)
    * [Direcory Structure](#building.structure)
    * [Using Gradle to Execute the Build Tasks](#building.gradle)
    * [The Gradle build script](#building.script)
* [Running JDoop](#running)
    * [The JDoop CLI](#running.cli)
    * [The Web Application](#running.web)
* [Design and Implementation](#design)
    * [Goals](#design.goals)
    * [The classes of the Core API](#design.api)
    * [The classes of the JDoop CLI](#design.cli)
    * [The classes of the JDoop Web Application](#design.web)
    * [The classes of the JDoop Web UI](#design.webui)
    * [The classes of the JDoop REST API](#design.rest)


## Overview {#overview}
Doop is a framework for Java pointer analysis implemented in Datalog (using the [Logicblox](http://www.logicblox.com/)
engine and dialect).
The JDoop framework operates atop Doop providing:

* A Java-based API for running points-to analyses for Java programs (JDoop Core API).
* A standalone application for running the analyses through a command-line interface (JDoop CLI).
* A web application for:
     * running the analyses through a web-based user interface (JDOOP WebUI).
     * providing a Restful end-point for running the analyses through a remote web service (JDoop REST API).
* A Resful client for contacting the remote web service (JDoop REST Client).

JDoop is implemented in Java and [Groovy](http://www.groovy-lang.org),
using [Gradle](http://www.gradle.org) as its build system to designate a unified code-base for supporting
all the above modes of JDoop operation (for example, JDoop can be embedded in any Java-based application as
a set of JAR and logic files, it can be invoked from the command-line, etc.).


## Building JDoop {#building}
This section describes the process of building JDoop and its various options.

### Clone the JDoop repository {#building.cloning}
Clone the JDoop repo from bitbucket:

    hg clone [repo-url]

### Directory Structure {#building.structure}
The directory structure of the repository follows the established conventions for Java projects. Specifically,
the repository contains the following directories:

* *gradle*: contains the Gradle wrapper (gradlew) files [^building.gradlew].
* *lib*: the custom runtime dependencies (the jars which cannot be automatically downloaded by Gradle, such as soot).
* *logic*: the logic files (the core Doop analysis framework).
* *src*: the source files of JDoop (including the Java and Groovy sources, static html/css/javascript files, web
         templates, etc).

The *src* directory is structured according to the conventions of Gradle's
[Java](http://gradle.org/docs/current/userguide/java_plugin.html),
[Groovy](http://gradle.org/docs/current/userguide/groovy_plugin.html),
[Application](http://gradle.org/docs/current/userguide/application_plugin.html),
[War](http://gradle.org/docs/current/userguide/war_plugin.html) and
[Jetty](http://gradle.org/docs/current/userguide/jetty_plugin.html)
plugins, containing the following sub-directories[^building.logic]:

* *main/groovy*: The JDoop Groovy sources (Core API, CLI, WebUI, REST API, REST Client).
* *main/java*: The JDoop Java sources (soot fact generation).
* *main/resources*: The default Log4j properties (and any other Java-based resource files).
* *main/webapp*: The web application files used for the the WebUI and the REST API, including the static css/js files
(e.g. of the Bootstrap Web front-end framework which is used for the Web UI), the server-side templates (in tpl)
and the WEB-INF/web.xml file (the deployment descriptor of the web app).

The use of Gradle allows us to gather all Doop-related source files in a single repo. The repo also contains:

* the Gradle build scripts (build.gradle and settings.gradle),
* the Gradle wrapper invocation scripts (gradlew and gradlew.bat),
* the .hgignore file.
* the README file and this documentation.

### Using Gradle to Execute the Build Tasks {#building.gradle}
After cloning the repo, we can execute the build task of choice by issuing the following:

    $ ./gradlew [name-of-task]

To list the build tasks supported, we can issue:

    $ ./gradlew tasks

The main tasks available are the following:

* *classes* - Assembles the Java and Groovy classes.
* *jar* - Assembles a jar archive containing the classes.
* *distTar* - Bundles the project as a tar archive. The file is the distribution artifact of the JDoop CLI and the
REST Client, as it contains JDoop as a self-contained, standalone Java application with libs and automatically-generated
OS specific scripts. This task should be used for generating the JDoop distro to publish to a web site.
* *distZip* - As above, but generates a zip archive.
* *installApp* - Installs JDoop CLI and REST Client in the build/install directory (identical to producing distTar
or distZip and unpacking the contents to the build/install directory).
* *run* - Runs the JDoop CLI directly (without installing it).
* *runClient* - Runs the JDoop REST Client directly (without installing it).
* *war* - Generates a war archive (the standard Java Web application format) with all the compiled classes, the webapp
content and the libraries. This file is a self-contained archive that can be deployed to any Java Application Server
(such as Tomcat, Jetty, JBoss, etc) for running either the JDoop WebUI or the JDoop REST API.
* *jettyRun* - Starts the embedded Jetty Server and deploys the project's files automatically. This task is used for
starting the WebUI and the REST API directly (without deployment to a web container).
* *clean* - Deletes the build directory (generated by Gradle for storing the build tasks' output).
* *javadoc* - Generates Javadoc API documentation for the Java source code.
* *groovydoc* - Generates Groovydoc API documentation for the Groovy source code.
* *createProperties* - Creates the default doop properties file.

### The Gradle build script {#building.script}
The Gradle build script (build.gradle) contains the settings and code required to execute the build tasks.
The script uses Gradle's Groovy-based DSL to:

* apply the Groovy, Application, War and Jetty plugins,
* set the source and target compatibility for the generated class files (currently, it has to be 1.6),
* define the name of the Main class (for the CLI),
* configure the ports of the embedded Jetty server,
* setup the jar repositories,
* define the project's compile-time and run-time dependencies,
* define the custom createProperties task,
* define the custom runClient task,
* customize the files to be included in the application distribution and the web app archive,
* configure environment variables and system settings for running the CLI or the WebUI directly.

## Running JDoop {#running}
This section describes the various options supported for running the JDoop variants.

### Running JDoop from the CLI {#running.cli}

#### Differences from the original Doop run script

#### Installing JDoop vs Running JDoop directly

### The Web application {#running.web}

#### The JDoop Web UI

#### The JDoop REST API


## Design and Implementation {#design}

### Goals {#design.goals}
The primary goals of the JDoop design are the following:

1. Offer an embeddable and multi-tenant Java/Groovy API for running the analyses.
2. Mimic the behavior of the original Doop run bash script as much as possible.
3. Support client/server use cases (Web UI, RESTful API).
4. Develop a unified code-base that is highly maintainable, flexible and extensible.

### The classes of the Core API {#design.api}
The core API is contained in the doop Groovy package and contains the following classes.

#### doop.core.AnalysisFactory
A Factory for creating analyses. The class provides the following public method:

    newAnalysis(String name, List<String> jars, Map<String, AnalysisOption> options)

which creates a new `doop.core.Analysis` object. The method checks and verifies that all provided information (name of
the analysis, jar files and options) is correct, throwing an exception in case of error. The checks performed are
based on the doop run script and are implemented using private or protected methods.

This class is extended by `doop.CommandLineAnalysisFactory` and `doop.web.WebFormAnalysisFactory` to support
creating `doop.core.Analysis` objects from the CLI and the WebUI/REST API respectively.

#### doop.core.Analysis
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

All the other methods of the class are either private or protected, as they are used to implement the "internal details"
of executing an analysis.

*Thoughts/Experiments/TODOs*

* Explore the possibility to provide an analysis execution DSL?
* Model the state of a running analysis?
* Monitor the progress of a running analysis?
* Provide hooks for reacting to analysis events (event listeners or callbacks)?
* Explore caching capabilities in the light of a multi-user, multi-tenant execution environment?

#### doop.core.AnalysisOption
A class that models an analysis option. Each option contains the following attributes:

* id -
* name -
* description -
* value -
* forPreprocessor -
* flagType -
* cli -
* webUI -
* argName -
* isAdvanced -
* isFile -

The use of this class allows us to significantly simplify and reduce the code required to process and manage the
analysis options in the various JDoop usage scenarios (CLI, WEB UI, REST API).

#### doop.core.Doop
The low-level initialization point of the framework.

This class provides the following public method:

    void initDoop(String homePath, String outPath)

which sets the two main paths for each JDoop deployment:

* the doop home path, which determines the location of the logic files,
* the doop output path, which determines the location of the *out* directory generated by the framework.

The class also holds a list of all the available Analysis options (in the `ANALYSIS_OPTIONS` final field), providing:

* the `createDefaultAnalysisOptions()` method to obtain a `Map<String, AnalysisOption>` using
the default values defined in the `ANALYSIS_OPTIONS` list.
* the `createAnalysisOptions()` method to obtain a `Map<String, AnalysisOption>` using the
values loaded from a property file (placed either in $HOME/doop.properties or in $DOOP_HOME/doop.properties).
This method creates a new `Map<String, AnalysisOption>` using the default values and then loads the two properties
files consequently (if present) to override the default values. This way, we can support both "global" options (which
are applied to all analyses) and "personal" options (which are applied to a specific user's analyses).
* the `overrideAnalysisOptionsFromProperties(Map<String, AnalysisOption> options, Properties properties)`
method, which is used by the above method.

**Note**: To add a new analysis option to the framework, we need to:

* define the option in the `ANALYSIS_OPTIONS` list (and the CLI and the WebUI will automatically adjust to its usage). For
String options, it is currently necessary to define the argName of the option.
* implement the validation/checks required for the new option (if any) in the `doop.core.AnalysisFactory`,
* update the implementation of `doop.core.Analysis` to take into account the new option during the execution of the analysis phases.

*Thoughts/Experiments/TODOs*

* Support the provision of an additional properties file from the CLI?

#### doop.core.Helper

#### doop.core.ImportGenerator

#### Analysis inputs - the doop.resolve package

#### Other classes
doop.core.OS, doop.core.JRE, doop.core.PreprocessorFlag Enums and doop.preprocess Classes

### The classes of the JDoop CLI {#design.cli}

#### doop.Main

#### doop.CommandLineAnalysisFactory

### The classes of the JDoop Web Application {#design.web}

#### doop.web.Listener

#### doop.web.Config

#### doop.web.VelocityManager

#### doop.web.WebFormAnalysisFactory

#### doop.web.AnalysisState

#### doop.web.AnalysisRunner

#### doop.web.AnalysisManager

#### doop.web.AnalysisLRUMap


### The classes of the JDoop Web UI {#design.webui}

### The classes of the JDoop REST API {#design.rest}

[^about]: This document is to be used with [Pandoc](http://johnmacfarlane.net/pandoc/), using an invocation like the
following:

    pandoc -f markdown -t html -s -o outfile infile

[^building.gradlew]: Using the [Gradle wrapper](https://gradle.org/docs/current/userguide/gradle_wrapper.html) is the
suggested way to run Gradle, allowing us to build, run or deploy the project without installing Gradle manually (the
wrapper downloads Gradle for us).

[^building.logic]: Although we use Gradle's Application plugin, the logic files are placed in the *logic* top-level
directory and not in the *src/dist* sub-directory (which is the standard Gradle convention for placing files to be
distributed along with the application). This helps us support running the analyses without installing the JDoop app.