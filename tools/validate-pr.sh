#!/bin/bash

set +x
set -e

TRAVIS_SCALA_VERSION_ARG=$1

if [ "$TRAVIS_SCALA_VERSION_ARG" == 2.10* ]; then
  COMMAND="sbt ++$TRAVIS_SCALA_VERSION_ARG reactors210/test"
else
  COMMAND="sbt ++$TRAVIS_SCALA_VERSION_ARG test"
fi

if [ "$TRAVIS_PULL_REQUEST" == "false" ];
then
  $COMMAND
else
  $COMMAND
fi
