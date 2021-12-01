#!/usr/bin/env groovy

/* `buildPlugin` step provided by: https://github.com/jenkins-infra/pipeline-library */
buildPlugin(timeout: 180, configurations: [
        [platform: "docker && linux", jdk: "8", jenkins: null],
        [platform: "docker && linux", jdk: "11", jenkins: null]
])
