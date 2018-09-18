# Overview

The JMS Messaging Plugin provides the following functionality:

* A build trigger to submit jenkins jobs upon receipt of a matching message.
* A build and post-build step that may be used to submit a message to the topic upon the completion of a job.
* A build step to wait for a specific message.

JMS Messaging Providers

* This plugin currently only supports ActiveMQ at the moment.

# Development

This plugin contains a combination of regular JenkinsRule-based tests and [Acceptance Test Harness](https://github.com/jenkinsci/acceptance-test-harness) tests.

In order to execute the tests, we first have to package the plugin so that it is present in /plugins.

To build the plugin:

> mvn clean install -DskipTests

To run tests:

> mvn test

## Test Environment Requirements

* Docker
* Firefox

## CI

This plugin is currently built on [ci.jenkins.io](https://ci.jenkins.io/job/Plugins/job/jms-messaging-plugin/).

## Jenkinsfile Build Trigger Example

This example shows triggering your pipeline from Fedmsg with the org.fedoraproject.prod.buildsys.build.state.change topic

````
pipelineTriggers(
        [[$class: 'CIBuildTrigger',
          noSquash: true,
          providerData: [
              $class: 'FedMsgSubscriberProviderData',
              name: 'fedmsg',
              overrides: [
                  topic: 'org.fedoraproject.prod.buildsys.build.state.change'
              ],
              checks: [
                  [field: 'name', expectedValue: '^kernel$'],
                  [field: 'new', expectedValue: '1'],
                  [field: 'release', expectedValue: '.*fc28$']
              ]
          ]
        ]]
)
````
