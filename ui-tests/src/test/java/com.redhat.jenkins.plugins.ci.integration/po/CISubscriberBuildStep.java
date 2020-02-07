package com.redhat.jenkins.plugins.ci.integration.po;

import org.jenkinsci.test.acceptance.po.AbstractStep;
import org.jenkinsci.test.acceptance.po.BuildStep;
import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.PageAreaImpl;

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
@Describable("CI Subscriber")
public class CISubscriberBuildStep extends AbstractStep implements BuildStep {

    public final Control providerData = control("/");
    public final Control overrides = control("providerData/overrides");
    public final Control topic = control("providerData/overrides/topic");
    public final Control queue = control("providerData/overrides/queue");
    public final Control selector = control("providerData/selector");
    public final Control checks = control("providerData/repeatable-add");
    public final Control variable = control("providerData/variable");
    public final Control timeout = control("providerData/timeout");

    public CISubscriberBuildStep(Job parent, String path) {
        super(parent, path);
    }

    public MsgCheck addMsgCheck() {
        checks.click();
        return new MsgCheck(this, "providerData/checks");
    }

    public static class MsgCheck extends PageAreaImpl {
        public final Control expectedValue = control("expectedValue");
        public final Control field = control("field");

        protected MsgCheck(CISubscriberBuildStep area, String path) {
            super(area, path);
        }
    }
}
