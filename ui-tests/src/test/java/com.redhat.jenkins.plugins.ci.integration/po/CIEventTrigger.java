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
    public final Control providers = control("repeatable-add");
    private int numProviders = 0;

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

    public ProviderData addProviderData() {
        try {
            if (numProviders > 0) {
                providers.click();
            }
            return new ProviderData(this, "providers" + (numProviders > 0 ? "[" + numProviders + "]" : "") + "/providerData");
        } finally {
            numProviders++;
        }
    }

    public static class ProviderData extends PageAreaImpl {
        public final Control overrides = control("overrides");
        public final Control topic = control("overrides/topic");
        public final Control selector = control("selector");
        public final Control checks = control("repeatable-add");

        protected ProviderData(CIEventTrigger area, String path) {
            super(area, path);
        }

        public MsgCheck addMsgCheck() {
            checks.click();
            return new MsgCheck(this, "checks");
        }
    }

    public static class MsgCheck extends PageAreaImpl {
        public final Control expectedValue = control("expectedValue");
        public final Control field = control("field");

        protected MsgCheck(ProviderData area, String path) {
            super(area, path);
        }
    }
}
