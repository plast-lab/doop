FROM ubuntu:focal

USER root

## package update
RUN apt-get update

## Set "Noninteractive" mode during Docker build
RUN echo 'debconf debconf/frontend select Noninteractive' | debconf-set-selections
## locales: Doop may hang for non-UTF8 locales
RUN apt-get install -y apt-utils busybox nano libterm-readline-perl-perl
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
RUN apt-get install -y cmake cpp autoconf automake bison build-essential clang doxygen flex g++ gdb git libncurses5-dev libtool libsqlite3-dev make mcpp python3 sqlite unzip zlib1g-dev wget curl
# RUN mkdir /souffle && wget https://github.com/souffle-lang/souffle/releases/download/1.5.1/souffle_1.5.1-1_amd64.deb -O /souffle/souffle_1.5.1-1_amd64.deb && apt-get install /souffle/souffle_1.5.1-1_amd64.deb
# RUN mkdir /souffle && wget https://github.com/souffle-lang/souffle/releases/download/2.0.2/souffle_2.0.2-1_amd64.deb -O /souffle/souffle_2.0.2-1_amd64.deb && apt-get install /souffle/souffle_2.0.2-1_amd64.deb
# Install Souffle from sources
RUN mkdir /souffle && wget https://github.com/souffle-lang/souffle/archive/refs/tags/2.1.zip -O /souffle/2.1.zip && cd /souffle && unzip 2.1.zip
RUN ls /souffle
RUN ls /souffle/souffle-2.1
RUN cmake --version
RUN apt-get install -y bash-completion lsb-release
RUN cd /souffle/souffle-2.1 && mkdir build && cmake -S . -B build && cmake --build build -j --target install

## ddlog
## Rust (binary release)
RUN mkdir /rust && cd /rust && wget https://static.rust-lang.org/dist/rust-1.57.0-x86_64-unknown-linux-gnu.tar.gz -O /rust/rust-1.57.0-x86_64-unknown-linux-gnu.tar.gz && tar xf rust-1.57.0-x86_64-unknown-linux-gnu.tar.gz
RUN cd /rust/rust-1.57.0-x86_64-unknown-linux-gnu && ./install.sh
## Haskell stack
RUN curl -sSL https://get.haskellstack.org/ | sh
## Clone ddlog (on specific commit)
RUN git clone https://github.com/vmware/differential-datalog.git /differential-datalog && cd /differential-datalog && git checkout v1.2.3 && stack install --local-bin-path /usr/local/bin
## Install Python dependency for ddlog Souffle converter.
RUN apt-get install -y python3-pip && pip3 install parglare==0.12.0
ENV DDLOG_DIR=/differential-datalog/

## Doop
RUN apt-get install -y openjdk-8-jdk openjdk-8-jre openjdk-8-jdk-headless radare2 unzip time
RUN git clone https://bitbucket.org/yanniss/doop-benchmarks.git
ENV DOOP_BENCHMARKS=/doop-benchmarks
ENV DOOP_PLATFORMS_LIB=/doop-benchmarks
ENV DOOP_CACHE=/data/cache
ENV DOOP_OUT=/data/out
RUN mkdir /doop
ARG DOOP_VERSION=doop-4.24.9
COPY $DOOP_VERSION.zip /doop/$DOOP_VERSION.zip
RUN cd /doop && unzip $DOOP_VERSION.zip
ENV DOOP_HOME=/doop/$DOOP_VERSION
ENV PATH="$PATH:$DOOP_HOME/bin"

## Revert to "Dialog" mode after Docker build
RUN echo 'debconf debconf/frontend select Dialog' | debconf-set-selections
