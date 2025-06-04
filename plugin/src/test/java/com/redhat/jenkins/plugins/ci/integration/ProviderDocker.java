package com.redhat.jenkins.plugins.ci.integration;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.test.acceptance.docker.Docker;

public class ProviderDocker extends Docker {
    public boolean isContainerReady(String cid, String tag) throws IOException, InterruptedException {
        ProcessBuilder bldr = cmd("logs").add(cid).build();
        Process prc = bldr.start();
        String output = IOUtils.toString(prc.getInputStream());
        int pExit = prc.waitFor();
        if (pExit == 0) {
            return output.contains(tag);
        }
        String error = IOUtils.toString(prc.getErrorStream());
        System.err.println("docker logs failed with code: " + pExit
                + (error != null ? " and output: " + error : " and provided no error output"));
        return false;
    }

    public boolean stopContainer(String cid) throws IOException, InterruptedException {
        ProcessBuilder bldr = cmd("stop").add(cid).build();
        Process prc = bldr.start();
        int rc = prc.waitFor();
        return rc == 0;
    }

    @Override
    public boolean isContainerRunning(String cid) throws IOException, InterruptedException {
        cid = cid.substring(0, 12);
        ProcessBuilder bldr = cmd("ps").add("-q").add("--filter").add("id=" + cid).build();
        Process prc = bldr.start();
        String output = IOUtils.toString(prc.getInputStream());
        int pExit = prc.waitFor();
        if (pExit == 0) {
            return output.contains(cid);
        }
        String error = IOUtils.toString(prc.getErrorStream());
        System.err.println("docker logs failed with code: " + pExit
                + (error != null ? " and output: " + error : " and provided no error output"));
        return false;
    }
}
