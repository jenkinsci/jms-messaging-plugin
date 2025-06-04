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
package com.redhat.utils;

import java.util.logging.Logger;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;

public class MessageUtils {

    private static final Logger log = Logger.getLogger(MessageUtils.class.getName());

    public static final String JSON_TYPE = "application/json";

    public static ListBoxModel doFillMessageTypeItems(String messageType) {
        MESSAGE_TYPE current = MESSAGE_TYPE.fromString(messageType);
        ListBoxModel items = new ListBoxModel();
        for (MESSAGE_TYPE t : MESSAGE_TYPE.values()) {
            items.add(new ListBoxModel.Option(t.toDisplayName(), t.name(), (t == current) || items.size() == 0));
        }
        return items;
    }

    public static ListBoxModel doFillProviderNameItems() {
        ListBoxModel items = new ListBoxModel();
        for (JMSMessagingProvider provider : GlobalCIConfiguration.get().getConfigs()) {
            items.add(provider.getName());
        }
        return items;
    }

    public enum MESSAGE_TYPE {
        CodeQualityChecksDone("code-quality-checks-done"), ComponentBuildDone("component-build-done"), Custom("custom"),
        EarlyPerformanceTestingDone("early-performance-testing-done"),
        EarlySecurityTestingDone("early-security-testing-done"), ImageUploaded("image-uploaded"),
        FunctionalTestCoverageDone("functional-test-coverage-done"), FunctionalTestingDone("functional-testing-done"),
        NonfunctionalTestingDone("nonfunctional-testing-done"), OotbTestingDone("ootb-testing-done"),
        PeerReviewDone("peer-review-done"), ProductAcceptedForReleaseTesting("product-accepted-for-release-testing"),
        ProductBuildDone("product-build-done"), ProductBuildInStaging("product-build-in-staging"),
        ProductTestCoverageDone("product-test-coverage-done"), PullRequest("pull-request"),
        SecurityChecksDone("security-checks-done"), TestingStarted("testing-started"),
        TestingCompleted("testing-completed"), Tier0TestingDone("tier-0-testing-done"),
        Tier1TestingDone("tier-1-testing-done"), Tier2IntegrationTestingDone("tier-2-integration-testing-done"),
        Tier2ValidationTestingDone("tier-2-validation-testing-done"), Tier3TestingDone("tier-3-testing-done"),
        UnitTestCoverageDone("unit-test-coverage-done"), UpdateDefectStatus("update-defect-status");

        private final String message;

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
            if (v.isEmpty())
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

    public static SendResult sendMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        String startMessage = "Sending message for job '" + build.getParent().getName() + "'.";
        log.info(startMessage);
        listener.getLogger().println(startMessage);
        GlobalCIConfiguration config = GlobalCIConfiguration.get();
        JMSMessagingProvider provider = config.getProvider(pdata.getName());
        if (provider != null) {
            JMSMessagingWorker worker = provider.createWorker(pdata, build.getParent().getName());
            SendResult sendResult = worker.sendMessage(build, listener, pdata);
            if (sendResult.isSucceeded()) {
                String completedMessage = "Sent successfully with messageId: " + sendResult.getMessageId();
                log.info(completedMessage);
                listener.getLogger().println(completedMessage);
            } else {
                String failedMessage = "Failed to send message with messageId: " + sendResult.getMessageId();
                log.info(failedMessage);
                listener.getLogger().println(failedMessage);
            }
            return sendResult;
        } else {
            String errorMessage = "Unable to find provider " + pdata.getName() + ".";
            log.severe(errorMessage);
            listener.getLogger().println(errorMessage);
            return null;
        }

    }
}
