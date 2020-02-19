package com.redhat.jenkins.plugins.ci;

import hudson.Extension;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.listeners.ItemListener;

import java.util.List;
import java.util.logging.Logger;

import jenkins.model.ParameterizedJobMixIn;

import com.redhat.jenkins.plugins.ci.threads.CITriggerThread;

@Extension
public class ProjectChangeListener extends ItemListener {
    private static final Logger log = Logger.getLogger(ProjectChangeListener.class.getName());

    @Override
    public void onDeleted (Item item) {
        // Called just before the job is deleted.
        if (item instanceof Job) {
            CIBuildTrigger cibt = ParameterizedJobMixIn.getTrigger((Job<?, ?>)item, CIBuildTrigger.class);
            if (cibt != null) {
                cibt.force(item.getFullName());
            }
        }
    }

    @Override
    public void onLocationChanged (Item item, String oldFullName, String newFullName) {
        if (item instanceof Job) {
            // Rename has already happened, and trigger is attached to item.
            CIBuildTrigger cibt = ParameterizedJobMixIn.getTrigger((Job<?, ?>)item, CIBuildTrigger.class);
            if (cibt != null) {
                // Unsubscribe with old name and re-subscribe with new name (item has new name already).
                cibt.rename(oldFullName);
            }
        }
    }

    @Override
    public void onUpdated(Item item) {
        super.onUpdated(item);
        CIBuildTrigger cibt = CIBuildTrigger.findTrigger(item.getFullName());
        if (cibt != null) {
            if (item instanceof AbstractProject) {
                AbstractProject<?, ?> project = (AbstractProject<?, ?>) item;
                List<CITriggerThread> triggerThreads = CIBuildTrigger.locks.get(item.getFullName());
                if (triggerThreads != null && triggerThreads.size() > 0) {
                    log.info("Getting trigger threads.");
                }
                if (triggerThreads != null && triggerThreads.size() > 0 && project.isDisabled()) {
                    // there is a trigger thread AND it is disabled. we stop it.
                    log.info("Job " + item.getFullName() + " may have been previously been enabled" +
                            " but is now disabled. Attempting to stop trigger thread(s)...");
                    cibt.force(item.getFullName());
                } else {
                    if ((triggerThreads == null || triggerThreads.size() == 0) && !project.isDisabled()) {
                        // Job may have been enabled. Let's start the trigger thread.
                        log.info("Job " + item.getFullName() + " may have been previously been disabled." +
                                " Attempting to start trigger thread(s)...");
                        cibt.start((Job) item, false);
                    }
                }
            }
        } else {
            log.info("No CIBuildTrigger found, forcing thread stop.");
            new CIBuildTrigger().force(item.getFullName());
        }
    }
}
