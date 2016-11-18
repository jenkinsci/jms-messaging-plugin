package com.redhat.utils;

import hudson.EnvVars;

import java.util.logging.Logger;

import org.apache.commons.lang.text.StrSubstitutor;

public class PluginUtils {

    private static final Logger log = Logger.getLogger(PluginUtils.class.getName());

    public static String getSubstitutedValue(String id, EnvVars env) {
        String text = id.replaceAll("\\$([a-zA-Z_]+[a-zA-Z0-9_]*)", "\\${$1}"); //replace $VAR instances with ${VAR}.
        StrSubstitutor sub1 = new StrSubstitutor(env);

        return sub1.replace(text).trim();
    }
}
