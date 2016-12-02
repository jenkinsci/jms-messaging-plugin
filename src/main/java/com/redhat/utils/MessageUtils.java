package com.redhat.utils;

import com.redhat.jenkins.plugins.ci.messaging.MessagingWorker;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;

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

    public static boolean sendMessage(Run<?, ?> build, TaskListener listener,
                                      String providerName,
                                      MESSAGE_TYPE type,
                                      String props,
                                      String content) throws InterruptedException, IOException {
        log.info("Sending CI message for job '" + build.getParent().getName() + "'.");
        GlobalCIConfiguration config = GlobalCIConfiguration.get();
        MessagingWorker worker =
                config.getProvider(providerName).createWorker(build.getParent
                        ().getName());
        return worker.sendMessage(build,
                listener,
                type,
                props,
                content);
    }

    private static void logIfPossible(PrintStream stream, String logMessage) {
        if (stream != null) stream.println(logMessage);
    }

}
