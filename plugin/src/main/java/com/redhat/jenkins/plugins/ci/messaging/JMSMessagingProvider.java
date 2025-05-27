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
package com.redhat.jenkins.plugins.ci.messaging;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public abstract class JMSMessagingProvider implements Describable<JMSMessagingProvider>, Serializable {

    private static final long serialVersionUID = -4572907003185115933L;

    protected String name;
    protected String topic;
    protected static final Logger log = Logger.getLogger(JMSMessagingProvider.class.getName());
    public final static String DEFAULT_PROVIDERNAME = "default";

    public String getName() {
        return name;
    }

    public String getTopic() {
        return topic;
    }

    public abstract JMSMessagingWorker createWorker(ProviderData pdata, String jobname);

    public abstract JMSMessageWatcher createWatcher(String jobname);

    public boolean verify(String json, List<MsgCheck> checks, String jobname) {
        if (checks == null || checks.size() == 0) {
            return true;
        }
        if (StringUtils.isBlank(json)) {
            // There are checks, but the json is empty. Must be false.
            return false;
        }

        try {
            DocumentContext context = null;
            try {
                context = JsonPath.parse(json);
            } catch (InvalidJsonException ije) {
                log.log(Level.SEVERE, jobname + ": Unable to parse JSON", ije);
            }
            if (context != null) {
                for (MsgCheck check : checks) {
                    if (!verify(context, check, jobname)) {
                        log.fine(jobname + ": msg check: " + check.toString() + " failed against JSON:\n" + json);
                        return false;
                    }
                }
                log.fine(jobname + ": All msg checks have passed");
                return true;
            } else {
                log.warning(jobname + ": DocumentContext is null");
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, jobname + ": Unexpected exception raised in verify.", e);
        }
        return false;
    }

    private boolean verify(DocumentContext context, MsgCheck check, String jobname) {
        try {
            String field = StringUtils.prependIfMissing(check.getField(), "$.");
            String actual = Objects.toString(context.read(field), "");
            String expected = StringUtils.defaultString(check.getExpectedValue());
            return Pattern.compile(expected).matcher(actual).find();
        } catch (PathNotFoundException pnfe) {
            log.fine(jobname + ": " + pnfe.getMessage());
        } catch (Exception e) {
            log.log(Level.SEVERE, jobname + ": Unexpected exception raised in verify", e);
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        JMSMessagingProvider that = (JMSMessagingProvider) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public abstract static class MessagingProviderDescriptor extends Descriptor<JMSMessagingProvider> {

        public static ExtensionList<MessagingProviderDescriptor> all() {
            return Jenkins.get().getExtensionList(MessagingProviderDescriptor.class);
        }
    }
}
