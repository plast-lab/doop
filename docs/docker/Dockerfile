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
RUN apt-get install -y cpp autoconf automake bison build-essential clang doxygen flex g++ gdb git libncurses5-dev libtool libsqlite3-dev make mcpp python sqlite zlib1g-dev wget curl
RUN mkdir /souffle && wget https://github.com/souffle-lang/souffle/releases/download/1.5.1/souffle_1.5.1-1_amd64.deb -O /souffle/souffle_1.5.1-1_amd64.deb && apt-get install /souffle/souffle_1.5.1-1_amd64.deb

## ddlog
## Rust (binary release)
RUN mkdir /rust && cd /rust && wget https://static.rust-lang.org/dist/rust-1.41.0-x86_64-unknown-linux-gnu.tar.gz -O /rust/rust-1.41.0-x86_64-unknown-linux-gnu.tar.gz && tar xf rust-1.41.0-x86_64-unknown-linux-gnu.tar.gz
RUN cd /rust/rust-1.41.0-x86_64-unknown-linux-gnu && ./install.sh
## Haskell stack
RUN curl -sSL https://get.haskellstack.org/ | sh
## Clone ddlog (on specific commit)
RUN git clone https://github.com/vmware/differential-datalog.git /differential-datalog && cd /differential-datalog && git checkout v0.19.0 && stack install --local-bin-path /usr/local/bin
## Install Python dependency for ddlog Souffle converter.
RUN apt-get install -y python2.7 python-pip && pip2 install parglare==0.9.2
ENV DDLOG_DIR=/differential-datalog/

## Doop
RUN apt-get install -y openjdk-8-jdk openjdk-8-jre openjdk-8-jdk-headless radare2 unzip
RUN git clone https://bitbucket.org/yanniss/doop-benchmarks.git
ENV DOOP_BENCHMARKS=/doop-benchmarks
ENV DOOP_PLATFORMS_LIB=/doop-benchmarks
ENV DOOP_CACHE=/data/cache
ENV DOOP_OUT=/data/out
RUN mkdir /doop
ARG DOOP_VERSION=doop-4.22.9
COPY $DOOP_VERSION.zip /doop/$DOOP_VERSION.zip
RUN cd /doop && unzip $DOOP_VERSION.zip
ENV DOOP_HOME=/doop/$DOOP_VERSION
ENV PATH="$PATH:$DOOP_HOME/bin"
