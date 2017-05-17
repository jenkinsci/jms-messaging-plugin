# Overview

The JMS Messaging Plugin provides the following functionality:

* A build trigger to submit jenkins jobs upon receipt of a matching message.
* A build and post-build step that may be used to submit a message to the topic upon the completion of a job.
* A build step to wait for a specific message.

JMS Messaging Providers

* This plugin currently only supports:
 * ActiveMQ
 * FedMsg

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

## Note surrounding certificates needed for FedMsg Message Signing

* Clone https://github.com/fedora-infra/fedmsg
* Checkout the *develop* branch.
* Navigate to the *fedmsg/test/test_certs* folder.
* Run the following:

```
source ./vars
./clean-all
./build-ca
./build-and-sign-key <applicationName>
```

* The following files should be then collected:
** *keys/ca.crt* (This is the CA cert that would need to be distributed to anyone who wants to listen securely to your message)
** *keys/<applicationName>.crt* (This is the certificate that should be specified in the FedMsg Messaging Provider for Certificate.)
** *keys/<applicationName>.key* (This is the private key that should be specified in the FedMsg Messaging Provider for Keystore.)

