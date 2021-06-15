This directory provides a configuration for building Docker images for
Doop using the unsupported/legacy (LogicBlox-based) PA-datalog engine.

Steps:

* Go to http://snf-705535.vm.okeanos.grnet.gr/agreement.html and
download PA-datalog .deb for Ubuntu 18.04. Save it in this directory.

* Build the normal Docker image in the parent directory. Edit The
Dockerfile in this (legacy) directory and change the following line to
refer to the image built in the previous step:

```
ARG DOOP_VERSION=doop-4.24.2
```

* Copy directory lb-logic to this directory:

```
cp -R ../../../lb-logic .
```

* Build the Docker image and build/start a container:

```
docker build -t doop-legacy .
mkdir -p ../doop-data
docker container create --shm-size=1G --name doop_legacy_container -v $(realpath ../doop-data):/data -t doop-legacy
docker start doop_legacy_container
docker exec -it doop_legacy_container /bin/bash
```

* In the container, run Doop with the `--Xlb` flag:

```
cd $DOOP_HOME
doop --Xlb ...
```
