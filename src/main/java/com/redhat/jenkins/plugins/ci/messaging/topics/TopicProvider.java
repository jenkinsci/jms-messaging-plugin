package com.redhat.jenkins.plugins.ci.messaging.topics;

import java.io.IOException;
import java.io.Serializable;

import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public abstract class TopicProvider implements Describable<TopicProvider>, Serializable {

    // With AMQ virtual topics consumers need to have a unique name. Subscribers using virtual
    // topics use the form "Consumer.<consumer-name>.VirtualTopic.<hierarchy-topic-name>". The
    // <consumer-name> value must be unique. This provider allows unique topic names (meaning
    // the <consumer-name> part is unique) to be generated.

    private static final long serialVersionUID = -5505891184928466956L;

    public abstract static class TopicProviderDescriptor extends Descriptor<TopicProvider> {

        public abstract String generatePublisherTopic();

        public abstract String generateSubscriberTopic();

        // Web methods.
        public void doGeneratePublisherTopic(StaplerRequest2 req, StaplerResponse2 resp) throws IOException {
            String topic = generatePublisherTopic();
            resp.getWriter().write((topic != null ? topic : ""));
        }

        public void doGenerateSubscriberTopic(StaplerRequest2 req, StaplerResponse2 resp) throws IOException {
            String topic = generateSubscriberTopic();
            resp.getWriter().write((topic != null ? topic : ""));
        }

        public static ExtensionList<TopicProviderDescriptor> all() {
            return Jenkins.get().getExtensionList(TopicProviderDescriptor.class);
        }
    }
}
