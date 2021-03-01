#!/usr/bin/env bash

set -e

DOOP_VERSION=4.23.8
DOOP_ID=doop-${DOOP_VERSION}

echo "Building ${DOOP_ID}..."

docker build --build-arg DOOP_VERSION=${DOOP_ID} -t ${DOOP_ID} .
echo 'Note: if docker build fails with .deb 404 errors, use --no-cache'

docker tag ${DOOP_ID} gfour/doop:${DOOP_VERSION}
docker push gfour/doop:${DOOP_VERSION}
