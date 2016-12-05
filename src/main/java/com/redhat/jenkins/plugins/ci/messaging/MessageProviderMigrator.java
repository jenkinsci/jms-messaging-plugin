package com.redhat.jenkins.plugins.ci.messaging;

import com.redhat.jenkins.plugins.ci.CIMessageBuilder;
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.CIMessageSubscriberBuilder;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractProject;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Project;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.logging.Logger;

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

    private static void updateCIMessageBuilder(Project p, CIMessageBuilder builder) {
        if (builder.getProviderName() == null && GlobalCIConfiguration.get().isMigrationInProgress()) {
            builder.setProviderName(GlobalCIConfiguration.get()
                    .getConfigs().get(0).getName());
            try {
                p.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void updateCIMessageNotifier(Project p, CIMessageNotifier builder) {
        if (builder.getProviderName() == null && GlobalCIConfiguration.get().isMigrationInProgress()) {
            builder.setProviderName(GlobalCIConfiguration.get()
                    .getConfigs().get(0).getName());
            try {
                p.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private static void updateCIMessageSubscriberBuilder(Project p, CIMessageSubscriberBuilder builder) {
        if (builder.getProviderName() == null && GlobalCIConfiguration.get().isMigrationInProgress()) {
            builder.setProviderName(GlobalCIConfiguration.get()
                    .getConfigs().get(0).getName());
            try {
                p.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void migrateCIMessageBuilders() {
        Jenkins instance = Jenkins.getInstance();
        if (instance == null) { return; }
        log.info("Attempting to migrate all CIMessageBuilders and CIMessageSubscriberBuilders are valid.");
        for (BuildableItemWithBuildWrappers item : instance.getItems(BuildableItemWithBuildWrappers.class)) {
            Project p = (Project) item.asProject();
            for (Object builderObj : (p.getBuildersList().getAll(CIMessageBuilder.class))) {
                updateCIMessageBuilder(p, (CIMessageBuilder)builderObj);
            }
            for (Object notifierObj : (p.getPublishersList().getAll(CIMessageNotifier.class))) {
                updateCIMessageNotifier(p, (CIMessageNotifier)notifierObj);
            }
            for (Object builderObj : (p.getBuildersList().getAll(CIMessageSubscriberBuilder.class))) {
                updateCIMessageSubscriberBuilder(p, (CIMessageSubscriberBuilder)builderObj);
            }
        }
        log.info("Done");
    }
}
