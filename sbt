#!/bin/bash
SBT_OPTS="-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled"
java $SBT_OPTS -jar `pwd -P`/bin/sbt-launch.jar "$@"

