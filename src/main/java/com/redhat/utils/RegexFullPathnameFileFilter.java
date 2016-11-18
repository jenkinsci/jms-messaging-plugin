package com.redhat.utils;

import java.io.File;
import java.io.Serializable;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.AbstractFileFilter;

public class RegexFullPathnameFileFilter extends AbstractFileFilter implements Serializable {
    private static final Logger log = Logger.getLogger(RegexFullPathnameFileFilter.class.getName());

    private static final long serialVersionUID = -5321202059543342087L;
    private final Pattern pattern;

    public RegexFullPathnameFileFilter(final String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern is missing");
        }

        this.pattern = Pattern.compile(pattern);
    }

    public RegexFullPathnameFileFilter(final String pattern, final IOCase caseSensitivity) {
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern is missing");
        }
        int flags = 0;
        if (caseSensitivity != null && !caseSensitivity.isCaseSensitive()) {
            flags = Pattern.CASE_INSENSITIVE;
        }
        this.pattern = Pattern.compile(pattern, flags);
    }

    public RegexFullPathnameFileFilter(final String pattern, final int flags) {
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern is missing");
        }
        this.pattern = Pattern.compile(pattern, flags);
    }

    public RegexFullPathnameFileFilter(final Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern is missing");
        }

        this.pattern = pattern;
    }

    @Override
    public boolean accept(final File dir, final String name) {
        return pattern.matcher(dir.getPath() + File.separator + name).find();
    }
}