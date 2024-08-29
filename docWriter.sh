#!/bin/bash

mvn package
java -jar ./target/*.jar --srcDir=$(pwd)/src/test/java --logLevel=debug --maxFileToChange=2 --publicMethodDoc=true --nonPublicMethodDoc=true $@
