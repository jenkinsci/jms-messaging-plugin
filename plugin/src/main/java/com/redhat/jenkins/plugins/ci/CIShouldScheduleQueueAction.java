package com.redhat.jenkins.plugins.ci;

import java.util.List;

import hudson.model.Action;
import hudson.model.Queue.QueueAction;

public class CIShouldScheduleQueueAction implements QueueAction {

    public Boolean schedule;

    public CIShouldScheduleQueueAction(Boolean schedule) {
        this.schedule = schedule;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }

    @Override
    public boolean shouldSchedule(List<Action> actions) {
        return schedule;
    }

}
