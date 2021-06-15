ARG DOOP_VERSION=doop-4.24.2
FROM $DOOP_VERSION
# Duplicate definition of ARG, see https://docs.docker.com/engine/reference/builder/#understand-how-arg-and-from-interact
ARG DOOP_VERSION

USER root

## PA-datalog
RUN apt-get install -y libtcmalloc-minimal4 libgoogle-perftools4 protobuf-compiler libprotobuf-dev libprotobuf-java libboost-date-time1.65.1 libboost-filesystem1.65.1 libboost-iostreams1.65.1 libboost-program-options1.65.1 libboost-date-time1.65.1 libboost-system1.65.1 libboost-thread1.65.1 libboost-regex1.65.1 libcppunit-1.14-0
RUN mkdir /pa-datalog
COPY pa-datalog_0.5-1bionic.deb /pa-datalog
RUN dpkg -i /pa-datalog/pa-datalog_0.5-1bionic.deb && apt-get install -f
ENV LOGICBLOX_HOME=/opt/lb/pa-datalog
ENV LD_LIBRARY_PATH="${LOGICBLOX_HOME}/lib/cpp:$LD_LIBRARY_PATH"
ENV LB_PAGER_FORCE_START=1
ENV LC_ALL=en_US.UTF-8
ENV PATH="$PATH:${LOGICBLOX_HOME}/bin:/souffle/build/bin"

## Add legacy logic
ENV DOOP_HOME=/doop/$DOOP_VERSION
RUN mkdir $DOOP_HOME/lb-logic
COPY lb-logic $DOOP_HOME/lb-logic
ENV LB_PAGER_FORCE_START=1
