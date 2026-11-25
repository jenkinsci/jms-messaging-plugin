#!/usr/bin/env groovy

/* `buildPlugin` step provided by: https://github.com/jenkins-infra/pipeline-library */
buildPlugin(
  useContainerAgent: false, // Set to `false` if you need to use Docker for containerized tests
  timeout: 180,
  configurations: [
    [platform: 'docker && linux', jdk: 21],
])
