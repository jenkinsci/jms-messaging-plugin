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
package com.redhat.jenkins.plugins.ci.messaging.checks;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * A check that a Kafka record header matches an expected value (regex). Used by Kafka subscriber to filter messages by
 * headers.
 */
public class HeaderCheck implements Serializable, Describable<HeaderCheck> {
    private static final long serialVersionUID = 1L;

    private final String headerName;
    private final String expectedValue;

    @DataBoundConstructor
    public HeaderCheck(String headerName, String expectedValue) {
        this.headerName = headerName;
        this.expectedValue = expectedValue;
    }

    public String getHeaderName() {
        return headerName;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    /**
     * Returns true if the given Kafka record headers contain a header with {@link #getHeaderName()} whose value matches
     * {@link #getExpectedValue()} (regex).
     */
    public boolean matches(Headers headers) {
        if (headers == null || StringUtils.isBlank(headerName)) {
            return false;
        }
        Header header = headers.lastHeader(headerName);
        if (header == null || header.value() == null) {
            return false;
        }
        String actual = new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
        String expected = StringUtils.defaultString(expectedValue);
        return Pattern.compile(expected).matcher(actual).find();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HeaderCheck that = (HeaderCheck) o;
        return Objects.equals(headerName, that.headerName) && Objects.equals(expectedValue, that.expectedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(headerName, expectedValue);
    }

    @Override
    public String toString() {
        return String.format("HeaderCheck{headerName='%s', expectedValue='%s'}", headerName, expectedValue);
    }

    @Override
    public Descriptor<HeaderCheck> getDescriptor() {
        return Jenkins.get().getDescriptorByType(HeaderCheckDescriptor.class);
    }

    @Extension
    @Symbol("headerCheck")
    public static class HeaderCheckDescriptor extends Descriptor<HeaderCheck> {
        @Override
        public String getDisplayName() {
            return "Kafka header check";
        }
    }
}
