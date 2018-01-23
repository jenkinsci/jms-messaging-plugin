package com.redhat.utils;

import hudson.model.TaskListener;
import hudson.model.Run;

import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;

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
public class MessageUtils {

    private static final Logger log = Logger.getLogger(MessageUtils.class.getName());

    public static final String JSON_TYPE = "application/json";

    public static enum MESSAGE_TYPE {
        CodeQualityChecksDone("code-quality-checks-done"),
        ComponentBuildDone("component-build-done"),
        Custom("custom"),
        EarlyPerformanceTestingDone("early-performance-testing-done"),
        EarlySecurityTestingDone("early-security-testing-done"),
        ImageUploaded("image-uploaded"),
        FunctionalTestCoverageDone("functional-test-coverage-done"),
        FunctionalTestingDone("functional-testing-done"),
        NonfunctionalTestingDone("nonfunctional-testing-done"),
        OotbTestingDone("ootb-testing-done"),
        PeerReviewDone("peer-review-done"),
        ProductAcceptedForReleaseTesting("product-accepted-for-release-testing"),
        ProductBuildDone("product-build-done"),
        ProductBuildInStaging("product-build-in-staging"),
        ProductTestCoverageDone("product-test-coverage-done"),
        PullRequest("pull-request"),
        SecurityChecksDone("security-checks-done"),
        TestingStarted("testing-started"),
        TestingCompleted("testing-completed"),
        Tier0TestingDone("tier-0-testing-done"),
        Tier1TestingDone("tier-1-testing-done"),
        Tier2IntegrationTestingDone("tier-2-integration-testing-done"),
        Tier2ValidationTestingDone("tier-2-validation-testing-done"),
        Tier3TestingDone("tier-3-testing-done"),
        UnitTestCoverageDone("unit-test-coverage-done"),
        UpdateDefectStatus("update-defect-status");

        private String message;

        MESSAGE_TYPE(String value) {
            this.message = value;
        }

        public String getMessage() {
            return message;
        }

        public static MESSAGE_TYPE fromString(String value) {
            for (MESSAGE_TYPE t : MESSAGE_TYPE.values()) {
                if (value.equalsIgnoreCase(t.name())) {
                    return t;
                }
            }
            return null;
        }

        public String toDisplayName() {
            String v = name();
            if (v == null || v.isEmpty())
                return v;

            StringBuilder result = new StringBuilder();
            result.append(v.charAt(0));
            for (int i = 1; i < v.length(); i++) {
                if (Character.isUpperCase(v.charAt(i))) {
                    result.append(" ");
                }
                result.append(v.charAt(i));
            }
            return result.toString();
        }
    }

    public static SendResult sendMessage(Run<?, ?> build, TaskListener listener,
                                         String providerName,
                                         MessagingProviderOverrides overrides,
                                         MESSAGE_TYPE type,
                                         boolean failOnError, String props,
                                         String content) throws InterruptedException, IOException {
        log.info("Sending message for job '" + build.getParent().getName() + "'.");
        listener.getLogger().println("Sending message for job '" + build.getParent().getName() + "'.");
        GlobalCIConfiguration config = GlobalCIConfiguration.get();
        JMSMessagingWorker worker =
                config.getProvider(providerName).createWorker(overrides, build.getParent().getName());
        return worker.sendMessage(build,
                listener,
                type,
                props,
                content,
                failOnError);
    }

    private static void logIfPossible(PrintStream stream, String logMessage) {
        if (stream != null) stream.println(logMessage);
    }

}
