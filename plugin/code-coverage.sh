#!/bin/bash

mvn org.jacoco:jacoco-maven-plugin:0.7.9:prepare-agent -Daggregate=true clean package
mvn org.jacoco:jacoco-maven-plugin:0.7.9:report-aggregate -Daggregate=true
$BROWSER jth-tests/target/site/jacoco-aggregate/index.html
