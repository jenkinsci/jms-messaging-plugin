package com.redhat.utils;

import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.AbstractFileFilter;

import java.io.File;
import java.io.Serializable;
import java.util.regex.Pattern;

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
public class RegexFullPathnameFileFilter extends AbstractFileFilter implements Serializable {

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
