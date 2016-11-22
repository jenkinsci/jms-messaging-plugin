#!/usr/bin/env groovy

node('docker') {
    /* clean out the workspace just to be safe */
    deleteDir()

    /* Grab our source for this build */
    checkout scm

    stage 'Build'
    /* Performing some clever trickery to map our ~/.m2 into the container */
    String containerArgs = '-v $HOME/.m2:/var/maven/.m2'
    /* Make sure our directory is there, if Docker creates it, it gets owned by 'root' */
    sh 'mkdir -p $HOME/.m2'

    docker.image('maven:3.3-jdk-7').inside(containerArgs) {
        timestamps {
            sh 'mvn -B -U -e -Dmaven.test.failure.ignore=true -Duser.home=/var/maven clean install -DskipTests'
            sh 'mvn -B -U -e -Dmaven.test.failure.ignore=true -Duser.home=/var/maven test'
        }
    }

    stage 'Archive'
    junit 'target/surefire-reports/**/*.xml'
    archiveArtifacts artifacts: 'target/**/*.jar', fingerprint: true
}
