package com.redhat.jenkins.plugins.ci.integration.po;

import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.PageAreaImpl;
import org.jenkinsci.test.acceptance.po.WorkflowJob;

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
@Describable("CI event")
public class CIEventTrigger extends PageAreaImpl {
    public final Control noSquash = control("noSquash");
    public final Control providerData = control("/");
    public final Control overrides = control("providerData/overrides");
    public final Control topic = control("providerData/overrides/topic");
    public final Control selector = control("providerData/selector");
    public final Control checks = control("providerData/repeatable-add");

    public CIEventTrigger(Job parent) {
        super(parent, createPath(parent));
        control("").check();
    }

    private static String createPath(Job parent) {
        if (parent instanceof WorkflowJob) {
            return "/properties/org-jenkinsci-plugins-workflow-job-properties-PipelineTriggersJobProperty/triggers/com-redhat-jenkins-plugins-ci-CIBuildTrigger";
        }
        return "/com-redhat-jenkins-plugins-ci-CIBuildTrigger";
    }

    public MsgCheck addMsgCheck() {
        checks.click();
        return new MsgCheck(this, "providerData/checks");
    }

    public static class MsgCheck extends PageAreaImpl {
        public final Control expectedValue = control("expectedValue");
        public final Control field = control("field");

        protected MsgCheck(CIEventTrigger area, String path) {
            super(area, path);
        }
    }
}
