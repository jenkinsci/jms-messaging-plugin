package com.redhat.jenkins.plugins.ci.integration.docker.fixtures;

import java.io.IOException;

import org.jenkinsci.test.acceptance.docker.DockerContainer;
import org.jenkinsci.test.acceptance.docker.DockerFixture;

@DockerFixture(id="jbossamq", ports=61616)
public class JBossAMQContainer extends DockerContainer {

    public String getBroker() throws IOException {
        return "tcp://"+getIpAddress()+":61616";
    }
}
