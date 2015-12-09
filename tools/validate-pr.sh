#!/bin/sh

set +x

TRAVIS_SCALA_VERSION_ARG=$1

if [ $TRAVIS_PULL_REQUEST = "false" ];
then
  sbt $TRAVIS_SCALA_VERSION_ARG 'test'
else
	sbt $TRAVIS_SCALA_VERSION_ARG 'test'
fi
