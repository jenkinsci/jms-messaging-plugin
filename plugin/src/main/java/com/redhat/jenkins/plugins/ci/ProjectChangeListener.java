package com.redhat.jenkins.plugins.ci;

import java.util.List;
import java.util.logging.Logger;

import com.redhat.jenkins.plugins.ci.threads.CITriggerThread;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.listeners.ItemListener;
import jenkins.model.ParameterizedJobMixIn;

@Extension
public class ProjectChangeListener extends ItemListener {
    private static final Logger log = Logger.getLogger(ProjectChangeListener.class.getName());

    @Override
    public void onDeleted(Item item) {
        // Called just before the job is deleted.
        if (item instanceof Job) {
            CIBuildTrigger cibt = ParameterizedJobMixIn.getTrigger((Job<?, ?>) item, CIBuildTrigger.class);
            if (cibt != null) {
                cibt.force(item.getFullName());
            }
        }
    }

    @Override
    public void onLocationChanged(Item item, String oldFullName, String newFullName) {
        if (item instanceof Job) {
            // Rename has already happened, and trigger is attached to item.
            CIBuildTrigger cibt = ParameterizedJobMixIn.getTrigger((Job<?, ?>) item, CIBuildTrigger.class);
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
            if (item instanceof ParameterizedJobMixIn.ParameterizedJob) {
                ParameterizedJobMixIn.ParameterizedJob<?, ?> project = (ParameterizedJobMixIn.ParameterizedJob<?, ?>) item;
                List<CITriggerThread> triggerThreads = CIBuildTrigger.locks.get(item.getFullName());
                if (triggerThreads != null && !triggerThreads.isEmpty() && project.isDisabled()) {
                    log.info("Job " + item.getFullName() + " may have been previously enabled"
                            + " but is now disabled. Attempting to stop trigger thread(s)...");
                    cibt.force(item.getFullName());
                } else if ((triggerThreads == null || triggerThreads.isEmpty()) && !project.isDisabled()) {
                    log.info("Job " + item.getFullName() + " may have been previously disabled."
                            + " Attempting to start trigger thread(s)...");
                    cibt.start((Job<?, ?>) item, false);
                }
            }
        } else {
            // No trigger configured for this job — nothing to do.
            // Jenkins calls Trigger.stop() when a trigger is removed from a job,
            // so orphaned threads are already cleaned up by the framework.
            log.fine("No CIBuildTrigger found for '" + item.getFullName() + "', skipping.");
        }
    }
}
