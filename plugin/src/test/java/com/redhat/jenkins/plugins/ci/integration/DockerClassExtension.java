package com.redhat.jenkins.plugins.ci.integration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.test.acceptance.docker.Docker;
import org.jenkinsci.test.acceptance.docker.DockerContainer;
import org.jenkinsci.test.acceptance.docker.DockerImage;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 equivalent of the Docker fixtures' class rule. Builds the image once, supports creating
 * multiple containers during the class, and closes all created containers after the class finishes.
 */
public final class DockerClassExtension<T extends DockerContainer> implements BeforeAllCallback, AfterAllCallback {

    private final Class<T> containerType;
    private final List<T> containers = new ArrayList<>();
    private DockerImage image;

    public DockerClassExtension(Class<T> containerType) {
        this.containerType = containerType;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Docker docker = new Docker();
        assumeTrue(docker.isAvailable(), "Docker is needed for the test");
        image = docker.build(containerType);
    }

    public T create() throws IOException, InterruptedException {
        if (image == null) {
            throw new IllegalStateException("Docker image has not been initialized");
        }
        T container = image.start(containerType).start();
        containers.add(container);
        return container;
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        Exception failure = null;
        for (T container : containers) {
            try {
                container.close();
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
