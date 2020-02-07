package com.redhat.jenkins.plugins.ci.messaging;

import com.redhat.jenkins.plugins.ci.provider.data.*;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.matrix.MatrixProject;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Project;

import java.io.IOException;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import com.redhat.jenkins.plugins.ci.CIMessageBuilder;
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.CIMessageSubscriberBuilder;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;

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
@Extension
public class MessageProviderMigrator {

    private static final Logger log = Logger.getLogger(MessageProviderMigrator.class.getName());

    private static boolean updateCIMessageBuilder(AbstractProject<?, ?> p, CIMessageBuilder builder) {
        if (builder.getProviderData() == null) {
            if (builder.getProviderName() == null) {
                builder.setProviderName(GlobalCIConfiguration.get().getConfigs().get(0).getName());
                try {
                    p.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            JMSMessagingProvider prov = GlobalCIConfiguration.get().getProvider((builder.getProviderName()));
            if (prov instanceof ActiveMqMessagingProvider) {
                ActiveMQPublisherProviderData apd = new ActiveMQPublisherProviderData(builder.getProviderName());
                apd.setOverrides(builder.getOverrides());
                apd.setMessageType(builder.getMessageType());
                apd.setMessageProperties(builder.getMessageProperties());
                apd.setMessageContent(builder.getMessageContent());
                apd.setFailOnError(builder.isFailOnError());
                builder.setProviderData(apd);
            } else if (prov instanceof FedMsgMessagingProvider) {
                FedMsgPublisherProviderData fpd = new FedMsgPublisherProviderData(builder.getProviderName());
                fpd.setOverrides(builder.getOverrides());
                fpd.setMessageContent(builder.getMessageContent());
                fpd.setFailOnError(builder.isFailOnError());
                builder.setProviderData(fpd);
            } else {
                RabbitMQPublisherProviderData rpd = new RabbitMQPublisherProviderData(builder.getProviderName());
                rpd.setOverrides(builder.getOverrides());
                rpd.setMessageContent(builder.getMessageContent());
                rpd.setFailOnError(builder.isFailOnError());
                builder.setProviderData(rpd);
            }
            try {
                p.save();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static boolean updateCIMessageNotifier(AbstractProject<?, ?> p, CIMessageNotifier builder) {
        if (builder.getProviderData() == null) {
            if (builder.getProviderName() == null) {
                builder.setProviderName(GlobalCIConfiguration.get().getConfigs().get(0).getName());
                try {
                    p.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            JMSMessagingProvider prov = GlobalCIConfiguration.get().getProvider((builder.getProviderName()));
            if (prov instanceof ActiveMqMessagingProvider) {
                ActiveMQPublisherProviderData apd = new ActiveMQPublisherProviderData(builder.getProviderName());
                apd.setOverrides(builder.getOverrides());
                apd.setMessageType(builder.getMessageType());
                apd.setMessageProperties(builder.getMessageProperties());
                apd.setMessageContent(builder.getMessageContent());
                apd.setFailOnError(builder.isFailOnError());
                builder.setProviderData(apd);
            } else if (prov instanceof FedMsgMessagingProvider) {
                FedMsgPublisherProviderData fpd = new FedMsgPublisherProviderData(builder.getProviderName());
                fpd.setOverrides(builder.getOverrides());
                fpd.setMessageContent(builder.getMessageContent());
                fpd.setFailOnError(builder.isFailOnError());
                builder.setProviderData(fpd);
            } else {
                RabbitMQPublisherProviderData rpd = new RabbitMQPublisherProviderData(builder.getProviderName());
                rpd.setOverrides(builder.getOverrides());
                rpd.setMessageContent(builder.getMessageContent());
                rpd.setFailOnError(builder.isFailOnError());
                builder.setProviderData(rpd);
            }
            try {
                p.save();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
    private static boolean updateCIMessageSubscriberBuilder(AbstractProject<?, ?> p, CIMessageSubscriberBuilder builder) {
        if (builder.getProviderData() == null) {
            if (builder.getProviderName() == null) {
                builder.setProviderName(GlobalCIConfiguration.get().getConfigs().get(0).getName());
                try {
                    p.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            JMSMessagingProvider prov = GlobalCIConfiguration.get().getProvider((builder.getProviderName()));
            if (prov instanceof ActiveMqMessagingProvider) {
                ActiveMQSubscriberProviderData apd = new ActiveMQSubscriberProviderData(builder.getProviderName());
                apd.setOverrides(builder.getOverrides());
                apd.setSelector(builder.getSelector());
                apd.setChecks(builder.getChecks());
                apd.setVariable(builder.getVariable());
                apd.setTimeout(builder.getTimeout());
                builder.setProviderData(apd);
            } else if (prov instanceof FedMsgMessagingProvider) {
                FedMsgSubscriberProviderData fpd = new FedMsgSubscriberProviderData(builder.getProviderName());
                fpd.setOverrides(builder.getOverrides());
                fpd.setVariable(builder.getVariable());
                fpd.setTimeout(builder.getTimeout());
                builder.setProviderData(fpd);
            } else {
                RabbitMQSubscriberProviderData rpd = new RabbitMQSubscriberProviderData(builder.getProviderName());
                rpd.setOverrides(builder.getOverrides());
                rpd.setVariable(builder.getVariable());
                rpd.setTimeout(builder.getTimeout());
                builder.setProviderData(rpd);
            }
            try {
                p.save();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void migrateCIMessageBuilders() {
        Jenkins instance = Jenkins.getInstance();
        if (instance == null) { return; }
        if (GlobalCIConfiguration.get().isMigrationInProgress()) {
            log.info("isMigrationInProgress - > true | Forcing GlobalCIConfiguration.save()");
            GlobalCIConfiguration.get().save();
        }
        int updatedCount = 0;
        log.info("Attempting to migrate all CIMessageBuilders, CIMessageNotifier and CIMessageSubscriberBuilders build/publish steps");
        for (BuildableItemWithBuildWrappers item : instance.getItems(BuildableItemWithBuildWrappers.class)) {
            Job<?, ?> job = (Job<?, ?>) item;
            if (job instanceof Project) {
                Project<?, ?> p = (Project<?, ?>) item.asProject();
                for (Object builderObj : (p.getBuildersList().getAll(CIMessageBuilder.class))) {
                    if (updateCIMessageBuilder(p, (CIMessageBuilder)builderObj)) {
                        updatedCount++;
                    }
                }
                for (Object notifierObj : (p.getPublishersList().getAll(CIMessageNotifier.class))) {
                    if (updateCIMessageNotifier(p, (CIMessageNotifier)notifierObj)) {
                        updatedCount++;
                    }
                }
                for (Object builderObj : (p.getBuildersList().getAll(CIMessageSubscriberBuilder.class))) {
                    if (updateCIMessageSubscriberBuilder(p, (CIMessageSubscriberBuilder)builderObj)) {
                        updatedCount++;
                    }
                }
            }
            if (job instanceof MatrixProject) {
                MatrixProject p = (MatrixProject) item.asProject();
                for (Object builderObj : (p.getBuildersList().getAll(CIMessageBuilder.class))) {
                    if (updateCIMessageBuilder(p, (CIMessageBuilder)builderObj)) {
                        updatedCount++;
                    }
                }
                for (Object notifierObj : (p.getPublishersList().getAll(CIMessageNotifier.class))) {
                    if (updateCIMessageNotifier(p, (CIMessageNotifier)notifierObj)) {
                        updatedCount++;
                    }
                }
                for (Object builderObj : (p.getBuildersList().getAll(CIMessageSubscriberBuilder.class))) {
                    if (updateCIMessageSubscriberBuilder(p, (CIMessageSubscriberBuilder)builderObj)) {
                        updatedCount++;
                    }
                }
            }
        }
        log.info("Updated " + updatedCount + " build/publish step(s)");
    }
}
