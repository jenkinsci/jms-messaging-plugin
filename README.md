The JMS Messaging Plugin provides the following functionality:

-   A build trigger to submit jenkins jobs upon receipt of a matching
    message.
-   A post-build action that may be used to submit a message to the
    topic upon the completion of a job

JMS Messaging Providers

-   This plugin supports the following JMS Message Provider types
    -   ActiveMQ
    -   FedMsg
    -   RabbitMQ

Older versions of this plugin may not be safe to use. Please review the
following warnings before using an older version:

-   [SSRF
    vulnerability](https://jenkins.io/security/advisory/2019-02-19/#SECURITY-1033){.external-link}

## Set Up

### Global Configuration

Before the plugin may be used, you must add a JMS Messaging Provider.
This will provide the ability to send and receive messages. Below is a
list of currently supported JMS Message Providers:

-   ActiveMQ
-   FedMsg
-   RabbitMQ

![](https://wiki.jenkins.io/download/attachments/103088201/jms-messaging-screenshot.png?version=4&modificationDate=1491231804000&api=v2){.confluence-embedded-image
.confluence-content-image-border}

## Triggering

To enable the CI trigger, go to the job configuration page and add click
the check box "CI Event" under the Build Triggers section. Enabling the
trigger on a job requires two additional pieces of information:

-   Provider Name
-   JMS Selector

![](https://wiki.jenkins.io/download/attachments/103088201/trigger.png?version=1&modificationDate=1482413490000&api=v2){.confluence-embedded-image
.confluence-content-image-border}  
The complete documentation for JMS selectors can be found here:
<http://activemq.apache.org/selectors.html>

## Message Types

In the image above, the selector contains **CI\_TYPE =
'\<message-type\>'**

The valid values for **'\<message-type\>'** can be found in the
"**Type**" column in the table below:

| Message                                                | Description                                                                                                        | Type                                 |
|--------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| Code quality checks done                               | Indicate that static analysis covering code quality is complete.                                                   | code-quality-checks-done             |
| Component build done                                   | Indicate that a component has been built.                                                                          | component-build-done                 |
| Component functional test coverage done                | Indicate that test coverage for component tests is complete.                                                       | functional-test-coverage-done        |
| Custom                                                 | Indicate a custom event not covered by the other message types.                                                    | custom                               |
| Early performance testing done                         | Indicate that early automated performance testing is complete.                                                     | early-performance-testing-done       |
| Early security testing done                            | Indicate that early automated security testing is complete.                                                        | early-security-testing-done          |
| Engineering product build accepted for release testing | Indicate that engineering product build has been accepted as a release candidate.                                  | product-accepted-for-release-testing |
| Engineering product build in staging environment       | Indicate that engineering product build has been pushed to staging environment.                                    | product-build-in-staging             |
| Functional testing done                                | Indicate that all planned functional testing is complete.                                                          | functional-testing-done              |
| Image Uploaded                                         | Indicate that a cloud image has been uploaded                                                                      | image-uploaded                       |
| Nonfunctional testing done                             | Indicate that all planned nonfunctional testing is complete.                                                       | nonfunctional-testing-done           |
| Out of the box testing done                            | Indicate that end user testing is complete.                                                                        | ootb-testing-done                    |
| Peer review done                                       | Indicate that development has completed peer review of the code change.                                            | peer-review-done                     |
| Product build done                                     | Indicate that a new product build is complete.                                                                     | product-build-done                   |
| Product test coverage done                             | Indicate that test coverage for functional product tests is complete.                                              | product-test-coverage-done           |
| Pull request submitted                                 | Indicate that a code change needs to be reviewed and tested.                                                       | pull-request                         |
| Security checks done                                   | Indicate that static analysis covering security issues is complete.                                                | security-checks-done                 |
| Tier 0 testing done                                    | Indicate that tier 0 (unit) testing has completed.                                                                 | tier-0-testing-done                  |
| Tier 1 testing done                                    | Indicate that tier 1 (component) testing is complete.                                                              | tier-1-testing-done                  |
| Tier 2 build validation done                           | Indicate that the build validation is complete.                                                                    | tier-2-validation-testing-done       |
| Tier 2 integration testing done                        | Indicate that tier 2 (functional product) automated testing is complete.                                           | tier-2-integration-testing-done      |
| Tier 3 testing done                                    | Indicate that tier 3 testing is complete.                                                                          | tier-3-testing-done                  |
| Unit test coverage done                                | Indicate that test coverage for unit tests is complete.                                                            | unit-test-coverage-done              |
| Update defect status                                   | Indicate a code change to resolve a defect has been reviewed and tested and the defect status needs to be updated. | update-defect-status                 |

## Build Steps

### CI Notifier

This plugin provides a build step for publishing messages to the topic
upon job completion. To add the CI Notifier build step, go to the job
configuration page and select the "CI Notifier" option.

Adding the step to the job requires some additional information:

-   Provider name
-   Message type
-   Message properties
-   Message content.

The full list of message types available in the drop-down menu can be
found in the Message Type table above.

![](https://wiki.jenkins.io/download/attachments/103088201/ci-notifier.png?version=1&modificationDate=1482416658000&api=v2){.confluence-embedded-image
.confluence-content-image-border}

### CI Subscriber

This plugin provides a build step to wait for a specific message. To add
the CI Subscriber build step, go to the job configuration page and
select the "CI Subscriber" option.

Adding the step to the job requires some additional information:

-   Provider name
-   JMS Selector
-   Variable
-   Timeout

The build step will set an environment variable with the name from
Variable with a value of the message content.

![](https://wiki.jenkins.io/download/attachments/103088201/ci-subscriber-build.png?version=1&modificationDate=1482416989000&api=v2){.confluence-embedded-image
.confluence-content-image-border}

## Post-build steps

### CI Notifier

See above.

## Pipeline Support

### Triggers

The CI trigger is available via the triggers section in a declarative pipeline:

``` syntaxhighlighter-pre
triggers {
    ciBuildTrigger(noSquash: false,
        providerList: [ activeMQSubscriber(name: 'Red Hat UMB',
                        checks: [
                            [
                                expectedValue: '^foo.*bar$',
                                field: '$.msg.tag'
                            ]
                        ],
                        overrides: [topic: 'Consumer.rh-jenkins-ci-plugin.8dad9900-abcabc.VirtualTopic.eng.ci.example.durable.test.abcabc'] )
        ]
    )
}
```

## Job DSL Support

Here is a usage example for Job DSL:

``` syntaxhighlighter-pre
ciBuildTrigger {
    providers {
      providerDataEnvelope {
        providerData {
          activeMQSubscriber {
            name("Red Hat UMB")
            overrides {
              def uuid = "4ba46bbc-949b-11e8-b83f-54ee754ea14c"
              topic("Consumer.rh-jenkins-ci-plugin.${uuid}.VirtualTopic.eng.ci.redhat-container-image.pipeline.running")
            }
            // Message Checks
            checks {
              msgCheck {
                field('$.artifact.type')
                expectedValue("cvp")
              }
            }
          }
        }
      }
    }
    noSquash(true)
  }
}
```
### Steps

This plugin provides the steps when using the Jenkins Pipeline feature:

-   sendCIMessage
    -   This is the Freestyle CI Notifier Build and Publisher step
        sibling.
-   waitForCIMessage
    -   This is the Freestyle CI Subscriber Build step sibling.

Here are some examples:

``` syntaxhighlighter-pre
node('master') {
   // Send a message that CodeQualityChecksDone
    def sendResult = sendCIMessage \
        providerName: 'default', \
        messageContent: 'some content', \
        messageProperties: 'CI_STATUS = passed', \
        messageType: 'CodeQualityChecksDone'
    // echo sent message id and content
    echo sendResult.getMessageId()
    echo sendResult.getMessageContent()
}
```

and

``` syntaxhighlighter-pre
node('master') {
    // Wait for message and store message content in variable
    def msgContent = waitForCIMessage \
         providerName: 'default', \
         selector: "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'passed'"
    echo "msgContent = " + msgContent
}
```

## Building and Testing

Integration tests live in `ui-tests` using [Jenkins ATH](https://github.com/jenkinsci/acceptance-test-harness) and some will use Docker for the queue service.

When running Docker by default tests will use the Docker daemon ip and ports exposed. To use `localhost` and forwarded ports instead set the system property `-Ddocker.portforward=true`.

To [run with different browsers](https://github.com/jenkinsci/acceptance-test-harness/blob/master/docs/BROWSER.md) the environment variable `BROWSER` can be set, ie. `BROWSER=firefox`


## Change Log

#### Version 1.1.18 (April 23, 2020)

- Revert #181. Version 2.x will incorporate an improved ability to override the use of queues and topics
  on a job/pipeline step level.

#### Version 1.1.17 (April 19, 2020)

- Correct regression introduced in 1.1.16 that caused the CI Event configuration of Pipeline jobs
  to appear to have been reset upon configuring the job.

#### Version 1.1.16 (April 9, 2020)

**WARNING: This version contains a regression involving the display of job configuration data of Pipeline jobs.
 Please use v1.1.17**

- Correct presentation of data model for CI Notifier and CI Subscriber (#187) @scoheb
- Correct location of RabbitMQPublisherProviderData help files (#188) @scoheb

#### Version 1.1.15 (March 19, 2020)

- Allow sending notification to queue when useQueues is true (#181) @ArturHarasimiuk
- remove not used context dependency on hudson.Launcher (#182) @ArturHarasimiuk

#### Version 1.1.14 (March 12, 2020)

- Do not ignore Select/Choice parameters when triggering builds
- Set minimum core version to 2.150.2
- Add support for Fedora messaging wire format
- Bump jackson-databind from 2.9.10.1 to 2.9.10.3 in /plugin

#### Version 1.1.13 (Feb 20, 2020)

- When Jenkins is restarted, customized JMS configuration is silently reverted to same (default?) values (#170)
- Fix: Incorrect timestamp format with RabbitMq (#169)
- Fix: CI_MESSAGE has incorrect format with RabiitMq (#168)
- Move ath to module to escape the dependency hell (#164)
- Add example for triggers in pipeline dsl and job dsl (#160)

#### Version 1.1.12 (January 8, 2020)

- Update json-path from 2.3.0 to 2.4.0 (#129)
- Update jeromq from 0.4.0 to 0.4.3 (#128)
- Upgrade jackson-* packages (#156)
- Upgrade activemq-amqp to 5.15.9
- Add simple client for RabbitMQ (#154) (thanks @Zlopez)

#### Version 1.1.11 (November 6, 2019)

- Migrate to use GitHub README for plugin documentation (#153)

#### Version 1.1.10 (November 6, 2019)

- Correct handling of Boolean Parameters during triggering (#152)
- Ensure tests work (#150)
- Use HTTPS URLs in pom.xml (#151)
- Use mvn batch mode (#148)
- Message env var is not available in pipeline jobs (#143)

#### Version 1.1.9 (July 16, 2019) 

-   Ensure default values are used for build parameters when triggering
    builds (\#138)
-   Support Boolean Parameters (\#135)
-   Issue \#133: Message properties should do variable substitution on
    entire set of properties, not by line. (\#136)

#### Version 1.1.8 (June 5, 2019) 

-   Support Multi-line String parameter for CI\_MESSAGE definition
    (\#124)
    -   Fixes JENKINS-57642
-   Ensure timestamp is set when sending FedMsg (\#131)

#### Version 1.1.7 (May 21, 2019) 

-   add pipeline syntax support for CIBuildTrigger (\#106)
-   waitForCIMessage does not work correctly with message checks (\#114)
-   sendCIMessage gets stuck running when given nonexistent provider
    (\#116)
-   "CI event" build trigger keeps creating threads on Jenkins when
    given invalid client name (\#118)
-   Give users some sort of notification when a trigger has a fatal
    error (\#121)

#### Version 1.1.6 (Mar 05, 2019) 

-   Trigger on multiple topics. (\#111)
-   Fix pipeline snippet generator (\#113)

#### Version 1.1.5 (Feb 27, 2019) 

-   Fix fedmsg topic override problem and test (\#108)

#### Version 1.1.4 (Feb 21, 2019) 

-   Bump jackson-databind to address security issues (\#105)

#### Version 1.1.3 (Feb 15, 2019) 

-   getMsgId() for Fedmsg in jms-messaging plugin discrepancy.  (\#103)

#### Version 1.1.2 (Feb 11, 2019) 

-   Ensure only admins can perform testConnection() (\#104)

-   The jms-messaging-plugin defaults to org.fedoraproject if no topic
    override is specified (\#101)
-   Trigger thread is dying with an unhandled exception. (\#100)
-   Do not override PATH variable (\#88)

#### Version 1.1.1 (June 20, 2018) 

-   Fix defaults to work. Remove obsolete code. Some general cleanup.
    (\#87)

-   MESSAGE\_HEADERS should include all message headers. (\#82)

-   Update pom to new parent and fix deps (\#86)

#### Version 1.1.0 (June 12, 2018) 

-   MESSAGE\_HEADERS should include all message headers. (\#83)

-   Change provider selection to use a dropdownDescriptorSelector.
    (\#79)

#### Version 1.0.40 (May 25, 2018)

-   Correct possible empty CI\_MESSAGE using ActiveMQ.

#### Version 1.0.39 (May 17, 2018) 

  

Breaking change

**WARNING: Version 1.0.39 no longer supports a JMS selector for the
FedMsg Messaging Provider. You must update your jobs to add a Message
Check to specify what you want to trigger on.**

  

-   Fully implement message checks for AMQ messages.
-   Remove JMS selector and message properties from Fedmsg messages.
-   Only support JSON path for all message checks.
-   Fix issue where plugin was overwriting CI\_MESSAGE while jobs are
    queued. 

#### Version 1.0.38

-   not released

#### Version 1.0.37

-   not released

#### Version 1.0.36

-   Support global node properties in topics names for triggers (\#62)

#### Version 1.0.35

-   Force trigger thread to be stopped on trigger removal, project
    disable, and project delete. (\#61)

#### Version 1.0.34

-   Add @Whitelisted to MessagingProviderOverrides constructor. (\#60)

#### Version 1.0.32

-   Update CIMessageSendStep code to use CIMessageNotifier. Add methods
    to allow calls from pipeline code.

#### Version 1.0.31

-   Improve interrupt waitforcimessage (\#51)
-   add extra logging for waitForCIMessage (\#50)

#### Version 1.0.30

-   Fix SendResult to not require it to be Serializable

#### Version 1.0.29

-   sendCIMessage now returns SendResult (\#48)

#### Version 1.0.28

-   Add msgchecks to waitForMsg and fix timeout (\#47)

#### Version 1.0.27

-   added MsgCheck equals() to fix thread restart issue.

#### Version 1.0.26

-   Add support for JsonPath in MessageChecks (only FedMsg)

#### Version 1.0.25

-   Fix trigger thread not being restarted on JMS messaging provider
    change.

#### Version 1.0.24

-   Fix width of topic text boxes for overrides

#### Version 1.0.23

-   Add ability to use queues in AMQ messaging provider. (\#38)

#### Version 1.0.22

-   \<not released\>

#### Version 1.0.21

-   Rework to support quick and frequent stop/start of triggers with
    properties in Multibranch jobs

#### Version 1.0.20

-   Do not block Jenkins load when previous plugin's ancestor's
    descriptor has been detected.
-   Improve interpolation of environment variables in messages

-   Add logging for pipeline steps

-   Add failOnError support for sending messages

-   Add support for replacing tokens using previously defined properties

-   Refactor pipeline steps so logging and aborting works properly.

#### Version 1.0.19

-   fix missing fillProviderMethod for waitForCIMessage

-   fix NPE for GlobalCIConfiguration.getFirstProviderOverrides

#### Version 1.0.18

-   Improve interruptions of connection threads by ensuring that
    provider is connected before trying to interrupt and join trigger
    thread.

#### Version 1.0.17

-   Add topic provider.
-   Fix Tests for new selections

#### Version 1.0.16

-   Variable expansion now supported for override topics.

#### Version 1.0.15

-   (ActiveMQ) Improve resiliency by better cleanup failed connections.

#### Version 1.0.14

-   Upgrade ActiveMQ client to version 5.14.5.

#### Version 1.0.13

-   Allow a job to re-subscribe if job was previously disabled.
-   Fix migration steps to ensure null providers are fixed on startup.

#### Version 1.0.12

-   Fix for double-quotes in FedMsg selectors
-   Add MESSAGE\_HEADERS to the env for triggered jobs.
-   Allow topic configuration at build steps

#### Version 1.0.11

-   (JENKINS-42401) Enable trigger to work with Pipeline jobs

#### Version 1.0.10

-   Added Message Check support.

#### Version 1.0.9

-   Deleting project does not unsubscribe job nor shutdown subscriber
    thread.
-   Renaming project does not stop/start subscriber thread.

#### Version 1.0.8

-   Correct migration bug when plugins are installed that implement
    SaveableListener.

#### Version 1.0.7

-   Improve persistence of Keystore and Truststore config values.

#### Version 1.0.6

-   Support variable expansion in Keystore and Truststore config values.

#### Version 1.0.5

-   Introduction of SSL Certificate Authentication for ActiveMQ.

#### Version 1.0.4

-   FedMsg Message Provider
    -   Supports triggering
    -   Supports sending and receiving
    -   Signing of messages not yet implemented.

#### Version 1.0.3

-   Remove un-necessary dependencies

#### Version 1.0.2

-   Reduce required version of Jenkins Core

#### Version 1.0.1

-   Fix startup crash for project types Project and MatrixProject

#### Version 1.0.0

-   Initial release

