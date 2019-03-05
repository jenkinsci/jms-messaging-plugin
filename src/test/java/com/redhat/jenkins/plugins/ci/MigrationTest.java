package com.redhat.jenkins.plugins.ci;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import hudson.matrix.MatrixProject;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.triggers.Trigger;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import com.redhat.jenkins.plugins.ci.authentication.activemq.UsernameAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.topics.DefaultTopicProvider;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQSubscriberProviderData;

/**
 * Created by shebert on 07/12/16.
 */
public class MigrationTest {

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @LocalData
    @Test
    public void testUpgradeFromOnlyUserBaseAuth() throws Exception {
        assertTrue("config is not 1", GlobalCIConfiguration.get().getConfigs().size() == 1);

        JMSMessagingProvider config =
                GlobalCIConfiguration.get().getConfigs().get(0);
        ActiveMqMessagingProvider aconfig = (ActiveMqMessagingProvider) config;
        assertNotNull(aconfig.getAuthenticationMethod());
        assertTrue(aconfig.getAuthenticationMethod() instanceof UsernameAuthenticationMethod);
        UsernameAuthenticationMethod authMethod = (UsernameAuthenticationMethod) aconfig.getAuthenticationMethod();
        assertEquals("username should be scott", "scott", authMethod.getUsername());
        assertTrue(aconfig.getTopicProvider() instanceof DefaultTopicProvider);

        GlobalCIConfiguration newGlobalConfig = new GlobalCIConfiguration();
        JMSMessagingProvider config2 = newGlobalConfig.getConfigs().get(0);
        ActiveMqMessagingProvider aconfig2 = (ActiveMqMessagingProvider) config2;
        assertNotNull(aconfig2.getAuthenticationMethod());
    }

    @LocalData
    @Test
    public void testOtherJobTypes() throws Exception {
        assertTrue("config is not 1", GlobalCIConfiguration.get().getConfigs().size() == 1);
        assertNotNull(j.getInstance().getItem("maven"));
        Item matrixItem = j.getInstance().getItem
                ("matrix");
        assertNotNull(matrixItem);
        MatrixProject matrixJob = (MatrixProject)matrixItem;
        CIMessageBuilder builder = matrixJob.getBuildersList().get(CIMessageBuilder.class);
        assertNotNull(builder);
        assertEquals("Message Provider name should be default",
                "default", builder.getProviderName());
    }

    @LocalData
    @Test
    public void testConfig() throws Exception {
        assertTrue("config is not 1", GlobalCIConfiguration.get().getConfigs().size() == 1);

        JMSMessagingProvider config =
                GlobalCIConfiguration.get().getConfigs().get(0);
        ActiveMqMessagingProvider aconfig = (ActiveMqMessagingProvider) config;
        String topic = aconfig.getTopic();
        assertTrue("topic is not TOM", topic.equals("TOM"));

        AbstractProject triggerJob = (AbstractProject)j.getInstance().getItem("ci-trigger");

        Trigger trigger = triggerJob.getTrigger(CIBuildTrigger.class);
        assertNotNull(trigger);
        CIBuildTrigger cibt = ((CIBuildTrigger)triggerJob.getTrigger(CIBuildTrigger.class));
        assertTrue(cibt.getProviders().get(0) instanceof ActiveMQSubscriberProviderData);
        assertNotNull(cibt.getProviders().get(0).getName());
        assertNotNull(cibt.getSelector());

        FreeStyleProject notifierJob = (FreeStyleProject)j.getInstance().getItem("ci-notifier");

        CIMessageBuilder builder = notifierJob.getBuildersList().get(CIMessageBuilder.class);
        assertNotNull(builder);
        assertNotNull(builder.getProviderName());

        assertNotNull(notifierJob.getPublishersList());
        CIMessageNotifier notifierPublisher = notifierJob.getPublishersList().get(CIMessageNotifier.class);
        assertNotNull(notifierPublisher.getProviderName());

        FreeStyleProject subscriberJob = (FreeStyleProject)j.getInstance().getItem("ci-message-subscriber");
        CIMessageSubscriberBuilder subscriberBuilder =
                subscriberJob.getBuildersList().get(CIMessageSubscriberBuilder.class);
        assertNotNull(subscriberBuilder);
        assertNotNull(subscriberBuilder.getProviderName());

        GlobalCIConfiguration newGlobalConfig = new GlobalCIConfiguration();
        JMSMessagingProvider config2 = newGlobalConfig.getConfigs().get(0);
        ActiveMqMessagingProvider aconfig2 = (ActiveMqMessagingProvider) config2;
        assertNotNull(aconfig2.getAuthenticationMethod());
    }

    @LocalData
    @Test
    public void testAlreadyMigratedConfig() throws Exception {
        assertTrue("config is not 1", GlobalCIConfiguration.get().getConfigs().size() == 1);

        JMSMessagingProvider config =
                GlobalCIConfiguration.get().getConfigs().get(0);
        ActiveMqMessagingProvider aconfig = (ActiveMqMessagingProvider) config;
        String topic = aconfig.getTopic();
        assertTrue("topic is not CI", topic.equals("CI"));

        assertTrue(aconfig.getTopicProvider() instanceof DefaultTopicProvider);

        AbstractProject triggerJob = (AbstractProject)j.getInstance().getItem("ci-trigger");

        Trigger trigger = triggerJob.getTrigger(CIBuildTrigger.class);
        assertNotNull(trigger);
        assertNotNull(((CIBuildTrigger)triggerJob.getTrigger(CIBuildTrigger.class)).getProviderName());

        FreeStyleProject notifierJob = (FreeStyleProject)j.getInstance().getItem("ci-notifier");

        CIMessageBuilder builder = notifierJob.getBuildersList().get(CIMessageBuilder.class);
        assertNotNull(builder);
        assertNotNull(builder.getProviderName());

        assertNotNull(notifierJob.getPublishersList());
        CIMessageNotifier notifierPublisher = notifierJob.getPublishersList().get(CIMessageNotifier.class);
        assertNotNull(notifierPublisher.getProviderName());

        FreeStyleProject subscriberJob = (FreeStyleProject)j.getInstance().getItem("ci-message-subscriber");
        CIMessageSubscriberBuilder subscriberBuilder =
                subscriberJob.getBuildersList().get(CIMessageSubscriberBuilder.class);
        assertNotNull(subscriberBuilder);
        assertNotNull(subscriberBuilder.getProviderName());

    }
}

