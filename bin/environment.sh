#!/bin/bash

export JAVA_HOME= # e.g. "/usr/java/default" -- location of JAVA
export LOGICBLOX_HOME= # e.g. "$HOME/logicblox-3.10/logicblox" -- location of 'logicblox' directory
export DOOP_HOME= # e.g. "$HOME/doop" -- location of the doop repo
export DOOP_JRE_LIB= # e.g. "$HOME/doop-benchmarks/JREs" -- location of 'JREs' directory from the doop-benchmarks repo

export LB_PAGER_FORCE_START=true
export LB_MEM_NOWARN=true

export PATH=$DOOP_HOME/bin:$LOGICBLOX_HOME/bin:$JAVA_HOME/bin:$PATH
