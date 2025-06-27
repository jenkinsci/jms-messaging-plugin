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
package com.redhat.jenkins.plugins.ci.authentication.kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingProvider;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

public class NoneAuthenticationMethod extends KafkaAuthenticationMethod {
    private static final long serialVersionUID = 452156745621333871L;
    private transient static final Logger log = Logger.getLogger(NoneAuthenticationMethod.class.getName());

    private String credentialId;

    @DataBoundConstructor
    public NoneAuthenticationMethod() {
    }

    @Override
    public Descriptor<KafkaAuthenticationMethod> getDescriptor() {
        return Jenkins.get().getDescriptorByType(NoneAuthenticationMethodDescriptor.class);
    }

    @Extension
    @Symbol("none")
    public static class NoneAuthenticationMethodDescriptor extends AuthenticationMethodDescriptor {

        @Override
        public @Nonnull String getDisplayName() {
            return "No Authentication";
        }

        @Override
        public NoneAuthenticationMethod newInstance(StaplerRequest2 sr, JSONObject jo) {
            return new NoneAuthenticationMethod();
        }

        public String getConfigPage() {
            return "none.jelly";
        }

        @POST
        public FormValidation doTestConnection(@QueryParameter("name") String name,
                @QueryParameter("topic") String topic, @QueryParameter("producerProperties") String producerProperties,
                @QueryParameter("consumerProperties") String consumerProperties) {

            KafkaAuthenticationMethod.checkAdmin();

            KafkaMessagingProvider prov = new KafkaMessagingProvider(name, topic, producerProperties,
                    consumerProperties, new NoneAuthenticationMethod());

            Properties pprops = prov.getMergedProducerProperties();
            Properties cprops = prov.getMergedConsumerProperties();

            ClassLoader original = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(KafkaConsumer.class.getClassLoader());
            try (KafkaConsumer consumer = new KafkaConsumer<>(cprops);
                    KafkaProducer producer = new KafkaProducer<>(pprops)) {

                // Test producer.
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, "test-key", "test-value");
                producer.send(record).get();

                // Test consumer.
                consumer.subscribe(Collections.singletonList(topic));
                consumer.poll(Duration.ofMillis(100));

                return FormValidation.ok(Messages.SuccessBrokersConnect(pprops.get("bootstrap.servers"),
                        cprops.get("bootstrap.servers")));
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in KafkaMessagingProvider.doTestConnection: ", e);
                return FormValidation.error(Messages.Error() + ": " + e);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }
}
