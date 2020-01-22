FROM ubuntu:bionic

USER root

## package update
RUN apt-get update

## locales: Doop may hang for non-UTF8 locales
RUN apt-get install -y apt-utils busybox nano
# apt-utils installation should happen before locales installation
RUN apt-get install -y locales
RUN sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen
RUN echo 'LANG="en_US.UTF-8"' > /etc/default/locale
RUN dpkg-reconfigure --frontend=noninteractive locales
# RUN locale-gen en_US
# RUN locale-gen en_US.UTF-8
# RUN dpkg-reconfigure -f noninteractive locales

## timezone
# RUN apt-get install tzdata
# RUN echo "Europe/Athens" > /etc/timezone
# RUN dpkg-reconfigure -f noninteractive tzdata

## Souffle
RUN apt-get install -y cpp autoconf automake bison build-essential clang doxygen flex g++ gdb git libncurses5-dev libtool libsqlite3-dev make mcpp python sqlite zlib1g-dev
RUN git clone https://github.com/souffle-lang/souffle.git /souffle
RUN cd /souffle && git checkout 5ff4b52da14f8fd946429b8b16a94545326dcd0c && sh ./bootstrap && ./configure && make -j 4 && make install

## Doop
RUN apt-get install -y openjdk-8-jdk openjdk-8-jre openjdk-8-jdk-headless radare2 unzip
RUN git clone https://bitbucket.org/yanniss/doop-benchmarks.git
ENV DOOP_BENCHMARKS=/doop-benchmarks
ENV DOOP_PLATFORMS_LIB=/doop-benchmarks
ENV DOOP_CACHE=/data/cache
ENV DOOP_OUT=/data/out
RUN mkdir /doop
ARG DOOP_VERSION=doop-4.20.46
COPY $DOOP_VERSION.zip /doop/$DOOP_VERSION.zip
RUN cd /doop && unzip $DOOP_VERSION.zip
ENV DOOP_HOME=/doop/$DOOP_VERSION
ENV PATH="$PATH:$DOOP_HOME/bin"
