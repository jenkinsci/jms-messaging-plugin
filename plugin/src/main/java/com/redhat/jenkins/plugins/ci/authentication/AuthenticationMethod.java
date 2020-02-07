package com.redhat.jenkins.plugins.ci.authentication;

import java.io.Serializable;

import jenkins.model.Jenkins;

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
public abstract class AuthenticationMethod implements Serializable  {

    private static final long serialVersionUID = -6077120270692721571L;

    /**
     * Taken from gerrit-trigger-plugin
     */
    public static void checkAdmin() {
        final Jenkins jenkins = Jenkins.getInstance(); //Hoping this method doesn't change meaning again
        if (jenkins != null) {
            //If Jenkins is not alive then we are not started, so no unauthorised user might do anything
            jenkins.checkPermission(Jenkins.ADMINISTER);
        }
    }
}
