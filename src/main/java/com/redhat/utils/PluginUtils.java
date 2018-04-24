package com.redhat.utils;

import hudson.EnvVars;
import hudson.slaves.NodeProperty;
import hudson.slaves.EnvironmentVariablesNodeProperty;

import java.util.HashMap;
import java.util.Map;

import jenkins.model.Jenkins;

import org.apache.commons.lang.text.StrSubstitutor;

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
 */public class PluginUtils {

    public static String getSubstitutedValue(String id, EnvVars env) {
        if (id == null) {
            return id;
        }
        String text = id.replaceAll("\\$([a-zA-Z_]+[a-zA-Z0-9_]*)", "\\${$1}"); //replace $VAR instances with ${VAR}.
        if (env != null) {
            StrSubstitutor sub1 = new StrSubstitutor(env);
            text = sub1.replace(text).trim();
        }
        StrSubstitutor sub2 = new StrSubstitutor(getNodeGlobalProperties());
        return sub2.replace(text).trim();
    }


    public static Map<String, String> getNodeGlobalProperties() {
        Map<String, String> globalNodeProperties = new HashMap<String, String>();
        // Get latest global properties by looking for all
        // instances of EnvironmentVariablesNodeProperty in the global node properties
        for (NodeProperty<?> nodeProperty : Jenkins.getInstance()
                .getGlobalNodeProperties()) {
            if (nodeProperty instanceof EnvironmentVariablesNodeProperty) {
                globalNodeProperties.putAll(((EnvironmentVariablesNodeProperty) nodeProperty).getEnvVars());
            }
        }
        return globalNodeProperties;
    }
}
