#!/usr/bin/env groovy

/* `buildPlugin` step provided by: https://github.com/jenkins-infra/pipeline-library */
buildPlugin(timeout: 180, configurations: [
    [ platform: "windows", jdk: "8", jenkins: null ],
    [ platform: "docker && highmem", jdk: "8", jenkins: null ],
    [ platform: "docker && highmem", jdk: "11", jenkins: null ]
])
