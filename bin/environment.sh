#!/bin/bash

export JAVA_HOME= # e.g. "/usr/java/default" -- location of JAVA
export LOGICBLOX_HOME= # e.g. "/opt/lb/pa-datalog/logicblox" -- location of 'logicblox' directory
export DOOP_HOME= # e.g. "$HOME/doop" -- location of the doop repo
export DOOP_PLATFORMS_LIB= # e.g. "$HOME/doop-benchmarks" -- location of the doop-benchmarks repo

export LB_PAGER_FORCE_START=true
export LB_MEM_NOWARN=true

export PATH=$DOOP_HOME/bin:$LOGICBLOX_HOME/bin:$JAVA_HOME/bin:$PATH
