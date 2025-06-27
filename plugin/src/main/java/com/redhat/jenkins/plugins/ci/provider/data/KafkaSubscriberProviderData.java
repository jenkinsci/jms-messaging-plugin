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

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

public class KafkaSubscriberProviderData extends KafkaProviderData {

    private static final long serialVersionUID = -8999242275987307702L;

    public static final String DEFAULT_VARIABLE_NAME = "CI_MESSAGE";
    public static final String DEFAULT_RECORD_VARIABLE_SUFFIX = "_RECORD";
    public static final Integer DEFAULT_TIMEOUT_IN_MINUTES = 60;

    private List<MsgCheck> checks = new ArrayList<MsgCheck>();
    private String variable = DEFAULT_VARIABLE_NAME;
    private Boolean useFiles = false;
    private Integer timeout = DEFAULT_TIMEOUT_IN_MINUTES;
    private transient Boolean fromTrigger = false;

    @DataBoundConstructor
    public KafkaSubscriberProviderData() {
    }

    public KafkaSubscriberProviderData(String name) {
        this(name, null, null);
    }

    public KafkaSubscriberProviderData(String name, Boolean fromTrigger) {
        this(name, null, null);
        this.fromTrigger = fromTrigger;
    }

    public KafkaSubscriberProviderData(String name, MessagingProviderOverrides overrides, String properties) {
        super(name, overrides, properties);
    }

    public KafkaSubscriberProviderData(String name, MessagingProviderOverrides overrides, String properties,
            List<MsgCheck> checks, String variable, Boolean useFiles, Integer timeout) {
        this(name, overrides, properties);
        this.checks = checks;
        this.variable = variable;
        this.useFiles = useFiles;
        this.timeout = timeout;
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

    public String getRecordVariable() {
        return variable + DEFAULT_RECORD_VARIABLE_SUFFIX;
    }

    @Override
    public Boolean getFromTrigger() {
        return fromTrigger;
    }

    @Override
    public Descriptor<ProviderData> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(KafkaSubscriberProviderDataDescriptor.class);
    }

    @Override
    public boolean equals(Object that) {
        if (!super.equals(that)) {
            return false;
        }

        KafkaSubscriberProviderData thatp = (KafkaSubscriberProviderData) that;
        return Objects.equals(this.name, thatp.name) && Objects.equals(this.overrides, thatp.overrides)
                && Objects.equals(this.properties, thatp.properties) && Objects.equals(this.checks, thatp.checks)
                && Objects.equals(this.variable, thatp.variable) && Objects.equals(this.useFiles, thatp.useFiles)
                && Objects.equals(this.timeout, thatp.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, overrides, properties, checks, variable, useFiles, timeout);
    }

    @Extension
    @Symbol("kafkaSubscriber")
    public static class KafkaSubscriberProviderDataDescriptor extends KafkaProviderDataDescriptor {

        @Override
        public String getDisplayName() {
            return "Kafka Subscriber Provider Data";
        }

        @Override
        public KafkaSubscriberProviderData newInstance(StaplerRequest sr, JSONObject jo) {
            MessagingProviderOverrides mpo = null;
            if (!jo.getJSONObject("overrides").isNullObject()) {
                mpo = new MessagingProviderOverrides(jo.getJSONObject("overrides").getString("topic"));
            }
            String properties = jo.getString("properties");
            List<MsgCheck> checks = sr.bindJSONToList(MsgCheck.class, jo.get("checks"));
            String variable = null;
            if (jo.has("variable")) {
                variable = jo.getString("variable");
            }
            Integer timeout = null;
            if (jo.has("timeout") && !StringUtils.isEmpty(jo.getString("timeout"))) {
                timeout = jo.getInt("timeout");
            }
            return new KafkaSubscriberProviderData(jo.getString("name"), mpo, properties, checks, variable,
                    jo.getBoolean("useFiles"), timeout);
        }

        public String getDefaultVariable() {
            return DEFAULT_VARIABLE_NAME;
        }

        public Integer getDefaultTimeout() {
            return DEFAULT_TIMEOUT_IN_MINUTES;
        }

        @SuppressWarnings("lgtm[jenkins/no-permission-check]")
        @POST
        public FormValidation doCheckVariable(@QueryParameter String variable) {
            if (variable == null || variable.isEmpty()) {
                return FormValidation.error("Please enter a variable name to hold the received message result.");
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("lgtm[jenkins/no-permission-check]")
        @POST
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
            return "kafka-subscriber.jelly";
        }

    }

}
