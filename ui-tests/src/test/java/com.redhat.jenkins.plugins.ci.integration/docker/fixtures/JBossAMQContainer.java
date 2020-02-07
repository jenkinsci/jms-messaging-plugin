package com.redhat.jenkins.plugins.ci.integration.docker.fixtures;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import org.jenkinsci.test.acceptance.docker.DockerContainer;
import org.jenkinsci.test.acceptance.docker.DockerFixture;
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
@DockerFixture(id="jbossamq", ports=61616)
public class JBossAMQContainer extends DockerContainer {

    public String getBroker() throws IOException {
        String ip = getIpAddress();
        if (ip == null || ip.equals("")) {
            JsonNode binding = inspect().get("HostConfig").get("PortBindings").get("61616/tcp").get(0);
            String hostIP = binding.get("HostIp").asText();
            String hostPort = binding.get("HostPort").asText();
            ip = hostIP + ":" + hostPort;
            return "tcp://"+ip;
        } else {
            return "tcp://"+ip+":61616";
        }

    }
}
