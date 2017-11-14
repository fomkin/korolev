#!/bin/bash

OSNAME=`uname -s`
OSARCH=`uname -m`
SC_VERSION="4.4.10"

mkdir target
cd target

if [[ $OSNAME == *"Linux"* ]];
then
  if [[ $OSARCH == *"64"* ]];
  then
    wget https://saucelabs.com/downloads/sc-$SC_VERSION-linux.tar.gz
    tar -xzf sc-$SC_VERSION-linux.tar.gz
    mv sc-$SC_VERSION-linux sc-dist
  else
    if [[ $OSARCH == *"86"* ]];
    then
      wget https://saucelabs.com/downloads/sc-$SC_VERSION-linux32.tar.gz
      tar -xzf sc-$SC_VERSION-linux32.tar.gz
      mv sc-$SC_VERSION-linux32 sc-dist
    fi
  fi
else
  if [[ $OSNAME == *"Darwin"* ]];
  then
    curl -O https://saucelabs.com/downloads/sc-$SC_VERSION-osx.zip
    unzip sc-$SC_VERSION-osx.zip
    mv sc-$SC_VERSION-osx sc-dist
  fi
fi

cd ..
