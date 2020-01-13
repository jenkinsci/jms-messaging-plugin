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
package com.redhat.jenkins.plugins.ci;

import com.google.common.base.Strings;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Shell;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;

public class TriggerTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void variableAndParamInjection() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new Shell("env"));
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MY_PARAM", "foo")));

        CIBuildTrigger trigger = addCiBuildTrigger(p);

        QueueTaskFuture<AbstractBuild> qtf = trigger.scheduleBuild(Collections.singletonMap("CI_MESSAGE", "message content"));

        AbstractBuild build = j.assertBuildStatusSuccess(qtf);
        j.assertLogContains("CI_MESSAGE=message content", build);
        j.assertLogContains("MY_PARAM=foo", build);
    }

    @Test
    public void ignoreMessageThatIsTooLong() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new Shell("env"));

        CIBuildTrigger trigger = addCiBuildTrigger(p);

        QueueTaskFuture<AbstractBuild> qtf = trigger.scheduleBuild(Collections.singletonMap("CI_MESSAGE", Strings.repeat("a", 131061)));
        AbstractBuild build = j.assertBuildStatusSuccess(qtf);

    }

    private CIBuildTrigger addCiBuildTrigger(FreeStyleProject p) throws java.io.IOException {
        CIBuildTrigger trigger = new CIBuildTrigger();
        p.addTrigger(trigger);
        trigger.start(p, true);
        return trigger;
    }
}
