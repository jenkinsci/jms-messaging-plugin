package com.redhat.jenkins.plugins.ci.integration;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assume.assumeThat;

import java.io.File;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.test.acceptance.controller.JenkinsController;
import org.jenkinsci.test.acceptance.controller.LocalController;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.junit.Test;

import com.redhat.jenkins.plugins.ci.integration.po.CISubscriberBuildStep;

/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
@WithPlugins({"jms-messaging", "gearman-plugin"})
public class MigrationIntegrationTest extends AbstractJUnitTest {
    @Inject
    JenkinsController controller;

    @Test
    public void testMigrationWithSaveableListenersActive() throws Exception {
        assumeThat("TODO otherwise we would need to set up SSH access to push via Git, " +
                        "which seems an awful hassle", controller, instanceOf(LocalController.class));
        File jHome = ((LocalController) controller).getJenkinsHome();

        FileUtils.write(new File(jHome, "com.redhat.jenkins.plugins.ci.GlobalCIConfiguration.xml"),
                "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<com.redhat.jenkins.plugins.ci.GlobalCIConfiguration plugin=\"activemq-messaging@1.0.0-SNAPSHOT\">\n" +
                        "        <broker>tcp://dummy-broker.example.com:61616</broker>\n" +
                        "        <topic>TOM</topic>\n" +
                        "        <user>dummy</user>\n" +
                        "        <password>dummy</password>\n" +
                        "</com.redhat.jenkins.plugins.ci.GlobalCIConfiguration>\n");

        jenkins.restart();

        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        CISubscriberBuildStep subscriber = jobA.addBuildStep(CISubscriberBuildStep.class);
        subscriber.providerData.select("default");
        subscriber.selector.set("CI_TYPE = 'code-quality-checks-done'");
        subscriber.variable.set("HELLO");
        jobA.save();

    }

}
