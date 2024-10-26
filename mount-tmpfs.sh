#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
MAVEN_REPO_DIR="$(./mvnw help:evaluate -Dexpression=settings.localRepository -q -DforceStdout)/org/sharedtype"

function tmpfsify() {
  mkdir -p "$DIR/$1"
  sudo mount -t tmpfs -o size="$2" -o noatime tmpfs "$DIR/$1"
  echo "tmpfs mounted at $DIR/$1 of size $2"
}

tmpfsify "annotation/target" 128M
tmpfsify "processor/target" 256M
tmpfsify "it/target" 256M
tmpfsify $MAVEN_REPO_DIR 64M
