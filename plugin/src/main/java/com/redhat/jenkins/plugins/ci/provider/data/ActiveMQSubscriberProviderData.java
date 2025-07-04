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
package com.redhat.jenkins.plugins.ci.provider.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

public class ActiveMQSubscriberProviderData extends ActiveMQProviderData {
    private static final long serialVersionUID = -2179136605130421113L;

    public static final String DEFAULT_VARIABLE_NAME = "CI_MESSAGE";
    public static final String DEFAULT_HEADERS_VARIABLE_SUFFIX = "_HEADERS";
    public static final Integer DEFAULT_TIMEOUT_IN_MINUTES = 60;

    private String selector;
    private List<MsgCheck> checks = new ArrayList<>();
    private String variable = DEFAULT_VARIABLE_NAME;
    private Boolean useFiles = false;
    private Integer timeout = DEFAULT_TIMEOUT_IN_MINUTES;
    private transient Boolean fromTrigger = false;

    @DataBoundConstructor
    public ActiveMQSubscriberProviderData() {
    }

    public ActiveMQSubscriberProviderData(String name) {
        this(name, (MessagingProviderOverrides) null);
    }

    public ActiveMQSubscriberProviderData(String name, Boolean fromTrigger) {
        this(name);
        this.fromTrigger = fromTrigger;
    }

    public ActiveMQSubscriberProviderData(String name, MessagingProviderOverrides overrides) {
        super(name, overrides);
    }

    public ActiveMQSubscriberProviderData(String name, MessagingProviderOverrides overrides, String selector,
            List<MsgCheck> checks, String variable, Boolean useFiles, Integer timeout) {
        this(name, overrides);
        setSelector(selector);
        setChecks(checks);
        setVariable(variable);
        setUseFiles(useFiles);
        setTimeout(timeout);
    }

    public String getSelector() {
        return selector;
    }

    @DataBoundSetter
    public void setSelector(String selector) {
        this.selector = Util.fixEmpty(selector);
    }

    public List<MsgCheck> getChecks() {
        return checks;
    }

    @DataBoundSetter
    public void setChecks(List<MsgCheck> checks) {
        this.checks = checks;
    }

    public String getVariable() {
        return variable;
    }

    @DataBoundSetter
    public void setVariable(String variable) {
        this.variable = variable;
    }

    public Boolean getUseFiles() {
        return useFiles;
    }

    @DataBoundSetter
    public void setUseFiles(boolean useFiles) {
        this.useFiles = useFiles;
    }

    public Integer getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    // More descriptive name for callers.
    public String getMessageVariable() {
        return getVariable();
    }

    public String getHeadersVariable() {
        return variable + DEFAULT_HEADERS_VARIABLE_SUFFIX;
    }

    @Override
    public Boolean getFromTrigger() {
        return fromTrigger;
    }

    @Override
    public Descriptor<ProviderData> getDescriptor() {
        return Jenkins.get().getDescriptorByType(ActiveMQSubscriberProviderDataDescriptor.class);
    }

    @Override
    public boolean equals(Object that) {
        if (!super.equals(that)) {
            return false;
        }

        ActiveMQSubscriberProviderData thatp = (ActiveMQSubscriberProviderData) that;
        return Objects.equals(this.name, thatp.name) && Objects.equals(this.selector, thatp.selector)
                && Objects.equals(this.overrides, thatp.overrides) && Objects.equals(this.checks, thatp.checks)
                && Objects.equals(this.variable, thatp.variable) && Objects.equals(this.useFiles, thatp.useFiles)
                && Objects.equals(this.timeout, thatp.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, selector, overrides, checks, variable, useFiles, timeout);
    }

    @Override
    public String toString() {
        return "ActiveMQSubscriberProviderData{" + "overrides=" + overrides + ", selector='" + selector + '\''
                + ", checks=" + checks + ", variable='" + variable + '\'' + ", useFiles=" + useFiles + ", timeout="
                + timeout + ", name='" + name + '\'' + '}';
    }

    @Extension
    @Symbol("activeMQSubscriber")
    public static class ActiveMQSubscriberProviderDataDescriptor extends ActiveMQProviderDataDescriptor {

        @Override
        public @Nonnull String getDisplayName() {
            return "ActiveMQ Subscriber Provider Data";
        }

        @Override
        public ActiveMQSubscriberProviderData newInstance(StaplerRequest2 sr, JSONObject jo) {
            MessagingProviderOverrides mpo = null;
            if (!jo.getJSONObject("overrides").isNullObject()) {
                mpo = new MessagingProviderOverrides(jo.getJSONObject("overrides").getString("topic"));
            }
            List<MsgCheck> checks = sr.bindJSONToList(MsgCheck.class, jo.get("checks"));
            String variable = null;
            if (jo.has("variable")) {
                variable = jo.getString("variable");
            }
            Integer timeout = null;
            if (jo.has("timeout") && !StringUtils.isEmpty(jo.getString("timeout"))) {
                timeout = jo.getInt("timeout");
            }
            return new ActiveMQSubscriberProviderData(jo.getString("name"), mpo, jo.getString("selector"), checks,
                    variable, jo.getBoolean("useFiles"), timeout);
        }

        public String getDefaultVariable() {
            return DEFAULT_VARIABLE_NAME;
        }

        public Integer getDefaultTimeout() {
            return DEFAULT_TIMEOUT_IN_MINUTES;
        }

        public FormValidation doCheckSelector(@QueryParameter String selector) {
            if (selector == null || selector.isEmpty()) {
                return FormValidation.error("Please enter a JMS selector.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckVariable(@QueryParameter String variable) {
            if (variable == null || variable.isEmpty()) {
                return FormValidation.error("Please enter a variable name to hold the received message result.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckTimeout(@QueryParameter String timeout) {
            try {
                if (timeout == null || timeout.isEmpty() || Integer.parseInt(timeout) <= 0) {
                    return FormValidation.error("Please enter a positive timeout value.");
                }
            } catch (NumberFormatException e) {
                return FormValidation.error("Please enter a valid timeout value.");
            }
            return FormValidation.ok();
        }

        public String getConfigPage() {
            return "amq-subscriber.jelly";
        }

    }
}
