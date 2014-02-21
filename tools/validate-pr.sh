#!/bin/sh

if [[ $TRAVIS_PULL_REQUEST == "false" ]];
then
	echo "Only building pull requests - build passed."
	exit 0
else
	sbt ++${TRAVIS_SCALA_VERSION} \"testOnly org.reactress.test.*\"
fi
