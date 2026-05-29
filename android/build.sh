#!/bin/bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.8.9-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew assembleDebug --stacktrace
