package com.redhat.jenkins.plugins.ci.provider.data;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.List;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;

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
public class FedMsgSubscriberProviderData extends FedMsgProviderData {
    private static final long serialVersionUID = -2179136605130421113L;

    public static final String DEFAULT_VARIABLE_NAME = "CI_MESSAGE";
    public static final Integer DEFAULT_TIMEOUT_IN_MINUTES = 60;

    private MessagingProviderOverrides overrides;
    private List<MsgCheck> checks = new ArrayList<MsgCheck>();
    private String variable;
    private Integer timeout = DEFAULT_TIMEOUT_IN_MINUTES;

    @DataBoundConstructor
    public FedMsgSubscriberProviderData() {}

    public FedMsgSubscriberProviderData(String name) {
        this(name, null);
    }

    public FedMsgSubscriberProviderData(String name, MessagingProviderOverrides overrides) {
        super(name, overrides);
    }

    public FedMsgSubscriberProviderData(String name, MessagingProviderOverrides overrides, List<MsgCheck> checks, String variable, Integer timeout) {
        this(name, overrides);
        this.checks = checks;
        this.variable = variable;
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

    public Integer getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    @Override
    public Descriptor<ProviderData> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(FedMsgSubscriberProviderDataDescriptor.class);
    }

    @Override
    public boolean equals(Object that){
        if (!super.equals(that)) {
            return false;
        }

        FedMsgSubscriberProviderData thatp = (FedMsgSubscriberProviderData)that;
        return (this.name != null ? this.name.equals(thatp.name) : thatp.name == null) &&
               (this.overrides != null ? this.overrides.equals(thatp.overrides) : thatp.overrides == null) &&
               (this.checks != null ? this.checks.equals(thatp.checks) : thatp.checks == null) &&
               (this.variable != null ? this.variable.equals(thatp.variable) : thatp.variable == null) &&
               (this.timeout != null ? this.timeout.equals(thatp.timeout) : thatp.timeout == null);
    }

    @Extension
    public static class FedMsgSubscriberProviderDataDescriptor extends ProviderDataDescriptor {

        @Override
        public String getDisplayName() {
            return "FedMsg Subscriber Provider Data";
        }

        @Override
        public FedMsgSubscriberProviderData newInstance(StaplerRequest sr, JSONObject jo) {
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
            return new FedMsgSubscriberProviderData(jo.getString("name"), mpo, checks, variable, timeout);
        }

        public String getDefaultVariable() {
            return DEFAULT_VARIABLE_NAME;
        }

        public Integer getDefaultTimeout() {
            return DEFAULT_TIMEOUT_IN_MINUTES;
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
            return "fedmsg-subscriber.jelly";
        }

    }
}
