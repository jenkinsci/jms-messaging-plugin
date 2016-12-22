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

