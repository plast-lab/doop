# JDoop Documenation

This document describes the design, implementation and usage of the new Java-based Doop (JDoop - Doop with a Java driver).

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


<h2 id="overview">Overview</h2>


The JDoop framework is implemented in Java and [Groovy](http://www.groovy-lang.org),
using [Gradle](http://www.gradle.org) as its build system. JDoop operates atop the core Datalog/Logicblox-based Doop
analysis framework, providing:

* A standalone application for running the analyses through a command-line interface (JDoop CLI).
* A web application for:
    * running the analyses through a web-based user interface (JDOOP WebUI).
    * providing a restful end-point for running the analyses through a remote web service (JDoop REST API).

The above are backed by a single code-base that supports all modes of JDoop operation in a unified manner. In fact,
the JDoop code-base designates a "bare/core" Java-based API for running the analyses, thus supporting any possible custom
embedding/usage of the Doop analysis framework (for example, JDoop can be embedded in any Java-based application as
a set of JAR and logic files).


<h2 id="building">Building JDoop</h2>

<h3 id="building.cloning">Clone the JDoop repository</h3>
Clone the JDoop repo from bitbucket:

    hg clone [repo-url]

<h3 id="building.structure">Directory Structure</h3>
The directory structure of the repository follows the currently established conventions for Java projects. Specifically,
the repository contains the following directories:

* *gradle*: contains the Gradle wrapper (gradlew) files.
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
plugins, containing the following sub-directories:

* main/groovy: The JDoop Groovy sources (Core API, CLI, WebUI & REST API).
* main/java: The JDoop Java sources (soot fact generation).
* main/resources: The default Log4j properties (and any other Java-based resource files).
* main/webapp: The web application files used for the the WebUI and the REST API, including the static css/js files
(e.g. of the Bootstrap Web front-end framework which is used for the Web UI), the server-side templates (in tpl)
and the WEB-INF/web.xml file (the deployment descriptor of the web app).

The use of Gradle allows us to gather all Doop-related source files in a single repo. The repo also contains:

* the Gradle build scripts (build.gradle and settings.gradle),
* the Gradle wrapper invocation scripts (gradlew and gradlew.bat),
* the .hgignore file.
* this README file.

Notes

* Using the [Gradle wrapper](https://gradle.org/docs/current/userguide/gradle_wrapper.html) is the suggested way to
run Gradle, allowing us to build, run or deploy the project without installing Gradle manually (the wrapper downloads
Gradle for us).

* Although we use Gradle's Application plugin, the logic files are placed in the *logic* top-level directory and
not in the *src/dist* sub-directory (which is the standard Gradle convention for
placing files to be distributed along with the application). This helps us support running the analyses without
installing the JDoop app.

<h3 id="building.gradle">Using Gradle to Execute the Build Tasks</h3>
After cloning the repo, we can execute the build task of choice by issuing the following:

    $ ./gradlew [name-of-task]

To list the build tasks supported, we can issue:

    $ ./gradlew tasks

The main tasks available are the following:

* classes - Assembles the Java and Groovy classes.
* jar - Assembles a jar archive containing the classes.
* distTar - Bundles the project as a tar archive. The file is the distribution artifact of the JDoop CLI,
as it contains JDoop as a self-contained, standalone Java application with libs and automatically-generated OS specific
scripts. This task should be used for generating the JDoop distro to publish to a web site.
* distZip - As above, but generates a zip archive.
* installApp - Installs JDoop CLI in the build/install directory (identical to producing distTar or distZip and unpacking
the contents to the directory).
* run - Runs the JDoop CLI directly (without installing it).
* war - Generates a war archive (the standard Java Web application format) with all the compiled classes, the webapp
content and the libraries. This file is a self-contained archive that can be deployed to any Java Application Server
(such as Tomcat, Jetty, JBoss, etc) for running either the JDoop WebUI or the JDoop REST API.
* jettyRun - Starts the embedded Jetty Server and deploys the project's files as and where they are (starting the WebUI
and the REST API directly).
* clean - Deletes the build directory (generated by Gradle for storing the build tasks' output).
* javadoc - Generates Javadoc API documentation for the Java source code.
* groovydoc - Generates Groovydoc API documentation for the Groovy source code.
* createProperties - Custom task for creating the default doop properties file.

<h3 id="building.script">The Gradle build script</h3>
The Gradle build script (build.gradle) contains the settings and code required to execute the build tasks.
The script uses Gradle's Groovy-based DSL to:

* apply the Groovy, Application, War and Jetty plugins,
* set the source and target compatibility for the generated class files (currently, it has to be 1.6),
* define the name of the Main class (for the CLI),
* configure the ports of the embedded Jetty server,
* setup the jar repositories,
* define the project's compile-time and run-time dependencies,
* define the custom createProperties task,
* customize the files to be included in the application distribution and the web app archive,
* configure environment variables and system settings for running the CLI or the WebUI directly.

<h2 id="running">Running JDoop</h2>

<h3 id="running.cli">The JDoop CLI</h3>

#### Differences from the original Doop run script

#### Installing JDoop vs Running JDoop directly

<h3 id="running.cli">The Web application</h3>

#### The JDoop Web UI

#### The JDoop REST API


<h2 id="design">Design and Implementation</h2>

<h3 id="design.goals">Goals</h3>
The primary goals of the JDoop design are the following:

1. Offer an embeddable and multi-tenant Java/Groovy API for running the analyses.
2. Mimic the behavior of the original Doop run bash script as much as possible.
3. Support client/server use cases (Web UI, RESTful API).
4. Develop a unified code-base that is highly maintainable, flexible and extensible.

<h3 id="design.api">The classes of the Core API</h3>
The core API is contained in the doop Groovy package and contains the following classes.

#### doop.AnalysisFactory
A Factory for creating analyses. The class provides the following public method:

    newAnalysis(String name, List<String> jars, Map<String, AnalysisOption> options)

which creates a new `doop.Analysis` object. The method checks and verifies that all provided information (name of
the analysis, jar files and options) is correct, throwing an exception in case of error. The checks performed are
based on the doop run script and are implemented using private or protected methods.

This class is extended by `doop.CommandLineAnalysisFactory` and `doop.web.WebFormAnalysisFactory` to support
creating `doop.Analysis` objects from the CLI and the WebUI/REST API respectively.

#### doop.Analysis
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

#### doop.AnalysisOption
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

#### doop.Doop
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
* implement the validation/checks required for the new option (if any) in the `doop.AnalysisFactory`,
* update the implementation of `doop.Analysis` to take into account the new option during the execution of the analysis phases.

*Thoughts/Experiments/TODOs*

* Support the provision of an additional properties file from the CLI?

#### doop.Helper

#### doop.ImportGenerator

#### Analysis inputs - the doop.resolve package

#### Other classes
doop.OS, doop.JRE, doop.PreprocessorFlag Enums and doop.preprocess Classes

<h3 id="design.cli">The classes of the JDoop CLI</h3>

#### doop.Main

#### doop.CommandLineAnalysisFactory

<h3 id="design.web">The classes of the JDoop Web Application</h3>

#### doop.web.Listener

#### doop.web.Config

#### doop.web.VelocityManager

#### doop.web.WebFormAnalysisFactory

#### doop.web.AnalysisState

#### doop.web.AnalysisRunner

#### doop.web.AnalysisManager

#### doop.web.AnalysisLRUMap


<h3 id="design.webui">The classes of the JDoop Web UI</h3>

<h3 id="design.rest">The classes of the JDoop REST API</h3>
