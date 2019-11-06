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
