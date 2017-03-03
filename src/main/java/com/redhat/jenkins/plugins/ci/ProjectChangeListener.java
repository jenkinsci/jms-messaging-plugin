package com.redhat.jenkins.plugins.ci;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.listeners.ItemListener;
import jenkins.model.ParameterizedJobMixIn;

@Extension
public class ProjectChangeListener extends ItemListener {
    @Override
    public void onDeleted (Item item) {
        if (item instanceof Job) {
            CIBuildTrigger cibt = CIBuildTrigger.findTrigger(item.getFullName());
            if (cibt != null) {
                cibt.stop();
            }
        }
    }

    @Override
    public void onLocationChanged (Item item, String oldFullName, String newFullName) {
        if (item instanceof Job) {
            // Rename has already happened, and trigger is attached to item.
            CIBuildTrigger cibt = ParameterizedJobMixIn.getTrigger((Job)item, CIBuildTrigger.class);
            if (cibt != null) {
                // Unsubscribe with old name and re-subscribe with new name (item has new name already).
                cibt.rename(oldFullName);
            }
        }
    }
}
