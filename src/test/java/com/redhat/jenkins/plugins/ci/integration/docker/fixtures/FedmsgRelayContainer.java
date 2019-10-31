package com.redhat.jenkins.plugins.ci.integration.docker.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import org.jenkinsci.test.acceptance.docker.DockerContainer;
import org.jenkinsci.test.acceptance.docker.DockerFixture;

import java.io.IOException;

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
@DockerFixture(id="fedmsg-relay", ports={4001,2003,22})
public class FedmsgRelayContainer extends DockerContainer {

    public String getPublisher() throws IOException {
        String ip = super.getIpAddress();
        if (ip == null || ip.equals("")) {
            JsonNode binding = inspect().get("HostConfig").get("PortBindings").get("2003/tcp").get(0);
            String hostIP = binding.get("HostIp").asText();
            String hostPort = binding.get("HostPort").asText();
            ip = hostIP + ":" + hostPort;
            System.out.println(ip);
            return "tcp://"+ip;
        }
        return "tcp://"+getIpAddress()+":2003";
    }
    public String getHub() throws IOException {
        String ip = super.getIpAddress();
        if (ip == null || ip.equals("")) {
            JsonNode binding = inspect().get("HostConfig").get("PortBindings").get("4001/tcp").get(0);
            String hostIP = binding.get("HostIp").asText();
            String hostPort = binding.get("HostPort").asText();
            ip = hostIP + ":" + hostPort;
            System.out.println(ip);
            return "tcp://"+ip;
        }
        return "tcp://"+getIpAddress()+":4001";
    }
    public String getSshIPAndPort() throws IOException {
        String ip = super.getIpAddress();
        if (ip == null || ip.equals("")) {
            JsonNode binding = inspect().get("HostConfig").get("PortBindings").get("22/tcp").get(0);
            String hostIP = binding.get("HostIp").asText();
            String hostPort = binding.get("HostPort").asText();
            ip = hostIP + " -p " + hostPort;
            System.out.println(ip);
            return ip;
        }
        return getIpAddress()+" -p 22";
    }
}
