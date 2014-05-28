#!/bin/sh

set +x

TRAVIS_SCALA_VERSION_ARG=$1

if [[ $TRAVIS_PULL_REQUEST == "false" ]];
then
	echo "Only building pull requests - build passed."
	exit 0
else
	sbt $TRAVIS_SCALA_VERSION_ARG 'testOnly scala.reactive.test.*'
fi
