# Doop Docker container #

## Build ##

To create a Docker container for Doop:

(1) Buid Doop using the "distribution" task:

```
./gradlew distZip
```

(2) Find the resulting archive, e.g.,
"build/distributions/doop-4.24.9.zip" and copy it to the Dockerfile
directory.

```
cp build/distributions/doop-4.24.9.zip docs/docker/
```

(3) Edit file `docs/docker/Dockerfile` and set DOOP_VERSION to the
base name of the archive (omit the file extension).

(4) Run:

```
cd docs/docker
docker build -t doop-4.24.9 . --no-cache
```

(5) Create a local directory for Doop's cache/out directories (needed
since analysis data may not fit the default Docker filesystem size
limit):

```
mkdir doop-data
```

## Use ##

Create the container, passing the doop-data directory as "data" inside
the container (if `realpath` is not available, replace with absolute
path of doop-data):

```
docker container create --name doop_container -v $(realpath ./doop-data):/data -t doop-4.24.9
```

Start the container:
```
docker start doop_container
```

To connect to the container via shell:

```
docker exec -it doop_container /bin/bash
```

To run an analysis inside the container and check its output
relations:

```
root@af6ca7193b3d:/# cd $DOOP_HOME
root@af6ca7193b3d:/doop/doop-4.24.9# bin/doop -i /path/to/app.jar -a context-insensitive --id test
root@af6ca7193b3d:/doop/doop-4.24.9# ls /data/out/context-insensitive/test/database/
```

## Publishing in Docker Hub ##

The following commands publish the Doop image to the [gfour/doop repository](https://hub.docker.com/r/gfour/doop) in Docker Hub:

```
docker tag doop-4.24.9 gfour/doop:4.24.9
docker push gfour/doop:4.24.9
```

## Notes ##

* This container does not contain the old Datalog engine used for
  legacy logic (pa-datalog).

* The development environment inside the container is minimal: only
  busybox/nano are installed by default.

* For old builds of Doop (with dynamic "+" dependencies in Gradle),
  the Gradle build scripts may have to be edited prior to "distZip"
  for reproducibility.
