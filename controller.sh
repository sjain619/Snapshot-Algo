#!/bin/bash +vx
LIB_PATH=$"./lib/protobuf-java-3.7.0.jar"
java -classpath bin:$LIB_PATH Controller $1 $2
