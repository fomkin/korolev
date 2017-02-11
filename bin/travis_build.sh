#!/bin/bash

if [[ $TRAVIS_SCALA_VERSION == *"2.12"* ]]
then
  sbt ++$TRAVIS_SCALA_VERSION -Dfile.encoding=UTF8 -J-XX:MaxPermSize=1024M test
  ./bin/sauce_setup.sh
  ./bin/sauce_start.sh
  sbt ++$TRAVIS_SCALA_VERSION integration-tests/run
  ./bin/sauce_stop.sh
else
  sbt ++$TRAVIS_SCALA_VERSION -Dfile.encoding=UTF8 -J-XX:MaxPermSize=1024M test
fi
