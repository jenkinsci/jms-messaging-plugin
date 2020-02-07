package com.redhat.jenkins.plugins.ci.messaging;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.logging.Logger;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;

import com.redhat.jenkins.plugins.ci.authentication.rabbitmq.RabbitMQAuthenticationMethod.AuthenticationMethodDescriptor;
import com.redhat.jenkins.plugins.ci.authentication.rabbitmq.RabbitMQAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQProviderData;

public class RabbitMQMessagingProvider extends JMSMessagingProvider {

    private static final long serialVersionUID = 82154526798596907L;

    private static final Logger log = Logger.getLogger(RabbitMQMessagingProvider.class.getName());

    private String virtualHost;
    private String hostname;
    private Integer portNumber;
    private RabbitMQAuthenticationMethod authenticationMethod;
    private Connection connection;
    private String exchange;
    private String queue;

    @DataBoundConstructor
    public RabbitMQMessagingProvider(String name, String virtualHost,
                                     String hostname, Integer portNumber,
                                     String topic, String exchange, String queue,
                                     RabbitMQAuthenticationMethod authenticationMethod) {
        this.name = name;
        this.virtualHost = virtualHost;
        this.hostname = hostname;
        this.portNumber = portNumber;
        this.topic = topic;
        this.exchange = exchange;
        this.queue = queue;
        this.authenticationMethod = authenticationMethod;
    }

    public String getVirtualHost() { return virtualHost; }

    public String getHostname() { return hostname; }

    public Integer getPortNumber() { return portNumber; }

    public String getTopic() { return topic; }

    public String getExchange() { return exchange; }

    public String getQueue() { return queue; }

    public Connection getConnection() { return connection; }

    public void setConnection(Connection connection) { this.connection = connection; }

    public RabbitMQAuthenticationMethod getAuthenticationMethod() { return authenticationMethod; }

    public ConnectionFactory getConnectionFactory() {
        return authenticationMethod.getConnectionFactory(getHostname(), getPortNumber(), getVirtualHost());
    }

    @Override
    public JMSMessagingWorker createWorker(ProviderData pdata, String jobname) {
        return new RabbitMQMessagingWorker(this, ((RabbitMQProviderData)pdata).getOverrides(), jobname);
    }

    @Override
    public JMSMessageWatcher createWatcher(String jobname) {
        return new RabbitMQMessageWatcher(jobname);
    }

    @Override
    public Descriptor<JMSMessagingProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(RabbitMQMessagingProvider.RabbitMQProviderDescriptor.class);
    }

    @Extension
    public static class RabbitMQProviderDescriptor extends MessagingProviderDescriptor {
        @Override
        public String getDisplayName() {
            return "RabbitMQ";
        }

        public ExtensionList<AuthenticationMethodDescriptor> getAuthenticationMethodDescriptors() {
            return AuthenticationMethodDescriptor.all();
        }
    }
}
