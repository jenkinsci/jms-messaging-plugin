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
package com.redhat.jenkins.plugins.ci.integration;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.integration.fixtures.FedmsgRelayContainer;
import com.redhat.jenkins.plugins.ci.messaging.FedMsgMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.FedMsgPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.FedMsgSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.utils.MessageUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.test.acceptance.docker.DockerClassRule;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.util.Collections.singleton;

public class FedMsgMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {
    @ClassRule
    public static DockerClassRule<FedmsgRelayContainer> docker = new DockerClassRule<>(FedmsgRelayContainer.class);
    private static FedmsgRelayContainer fedmsgRelay = null;

    @BeforeClass
    public static void startBroker() throws Exception {
        fedmsgRelay = docker.create();
    }

    @Before
    public void setUp() throws Exception {
        GlobalCIConfiguration.get().setConfigs(Collections.singletonList(new FedMsgMessagingProvider(
                DEFAULT_PROVIDER_NAME, fedmsgRelay.getHub(), fedmsgRelay.getPublisher(), "org.fedoraproject"
        )));
    }

    @Override
    public ProviderData getSubscriberProviderData(String topic, String variableName, String selector, MsgCheck... msgChecks) {
        return new FedMsgSubscriberProviderData(
                DEFAULT_PROVIDER_NAME,
                overrideTopic(topic),
                Arrays.asList(msgChecks),
                "CI_MESSAGE",
                60
        );
    }

    @Override
    public ProviderData getPublisherProviderData(String topic, MessageUtils.MESSAGE_TYPE type, String properties, String content) {
        return new FedMsgPublisherProviderData("test", overrideTopic(topic), content, null);
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheck() throws Exception {
        _testSimpleCIEventSubscribeWithCheck();
    }

    @Test
    public void testSimpleCIEventTriggerWithTextArea() throws Exception {
        _testSimpleCIEventTriggerWithTextArea("{ \"message\": \"Hello\\nWorld\" }",
                "Hello\\nWorld");
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheckWithTopicOverride() throws Exception, InterruptedException {
        _testSimpleCIEventSubscribeWithCheckWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventSubscribeWithCheckWithTopicOverrideAndVariableTopic();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithPipelineSendMsg() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithPipelineSendMsg();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheck() throws Exception {
        _testSimpleCIEventTriggerWithCheck();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckNoSquash() throws Exception {
        _testSimpleCIEventTriggerWithCheckNoSquash();
    }

    @Test
    public void testSimpleCIEventTriggerWithRegExpCheck() throws Exception {
        _testSimpleCIEventTriggerWithRegExpCheck();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithTopicOverride() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventTriggerWithMultipleTopics() throws Exception {
        _testSimpleCIEventTriggerWithMultipleTopics();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithTopicOverrideAndVariableTopic();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckOnPipelineJob() throws Exception {
        _testSimpleCIEventTriggerWithCheckOnPipelineJob();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg();
    }

    @Test
    public void testSimpleCIEventSendAndWaitPipeline() throws Exception {
        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('master') {\n def scott = waitForCIMessage providerName: 'test'," +
                " topic: 'org.fedoraproject.otopic'" +
                "\necho \"scott = \" + scott}", true));
        wait.scheduleBuild2(0);

        WorkflowJob send = j.jenkins.createProject(WorkflowJob.class, "send");
        send.setDefinition(new CpsFlowDefinition("node('master') {\n sendCIMessage" +
                " providerName: 'test', " +
                " topic: 'org.fedoraproject.otopic'," +
                " messageContent: '{\"content\":\"abcdefg\"}'}", true));
        j.buildAndAssertSuccess(send);

        j.assertBuildStatusSuccess(wait.getLastBuild());
        String expected = "scott = {\"content\":\"abcdefg\"}";
        j.assertLogContains(expected, wait.getLastBuild());
    }

    @Test
    public void testSimpleCIEventSendAndWaitPipelineWithVariableTopic() throws Exception {
        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('master') {\n" +
                "    env.MY_TOPIC = 'org.fedoraproject.my-topic'\n" +
                "    def scott = waitForCIMessage providerName: \"test\", overrides: [topic: \"${env.MY_TOPIC}\"]\n" +
                "    echo \"scott = \" + scott\n" +
                "}", true));
        wait.scheduleBuild2(0);

        WorkflowJob send = j.jenkins.createProject(WorkflowJob.class, "send");
        send.setDefinition(new CpsFlowDefinition("node('master') {\n" +
                " env.MY_TOPIC = 'org.fedoraproject.my-topic'\n" +
                " sendCIMessage providerName: \"test\", overrides: [topic: \"${env.MY_TOPIC}\"], messageContent: '{ \"content\" : \"abcdef\" }'\n" +
                "}",true));
        send.save();
        j.buildAndAssertSuccess(send);

        String expected = "scott = {\"content\":\"abcdef\"}";
        Thread.sleep(1000);
        j.assertBuildStatusSuccess(wait.getLastBuild());
        j.assertLogContains(expected, wait.getLastBuild());
    }

    @Test
    public void testJobRenameWithCheck() throws Exception {
        _testJobRenameWithCheck();
    }

    @Test
    public void testDisabledJobDoesNotGetTriggeredWithCheck() throws Exception {
        _testDisabledJobDoesNotGetTriggeredWithCheck();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {
        _testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic();
    }

    @Test
    public void testAbortWaitingForMessageWithPipelineBuild() throws Exception {
        _testAbortWaitingForMessageWithPipelineBuild();
    }


    @Test
    public void testTriggerWithComplexCheck() throws Exception {

        String packages = "(acl|atk|atomic|atomic-devmode|attr|audit|erlang|audit-libs|authconfig|avahi|basesystem|bash|bash-completion|bind|bind99|biosdevname|boost|bridge-utils|bwidget|bzip2|ca-certificates|cairo|c-ares|ceph|checkpolicy|chkconfig|chrony|cloud-init|cloud-utils|cockpit|conntrack-tools|container-selinux|coreutils|cpio|cracklib|criu|crypto-policies|cryptsetup|cups|curl|cyrus-sasl|dbus|dbus-glib|dbus-python|dejavu-fonts|deltarpm|device-mapper-libs|device-mapper-multipath|device-mapper-persistent-data|dhcp|diffutils|ding-libs|dmidecode|dnf|dnsmasq|docker|dracut|dracut-network|e2fsprogs|efibootmgr|efivar|elfutils|emacs|etcd|ethtool|euca2ools|expat|fedora-logos|fedora-release|fedora-repos|file|filesystem|findutils|fipscheck|fipscheck-lib|flannel|fontconfig|fontpackages|freetype|fuse|gawk|gc|gcc|gdbm|gdisk|gdk-pixbuf2|GeoIP|GeoIP-GeoLite-data|gettext|glib2|glibc|glib-networking|glusterfs|gmp|gnupg|gnupg2|gnutls|gobject-introspection|gomtree|gperftools|gpgme|gpm|gpm-libs|graphite2|grep|grub2|gsettings-desktop-schemas|gssproxy|guile|gzip|harfbuzz|hawkey|hdparm|hicolor-icon-theme|hostname|http-parser|hwdata|initscripts|ipcalc|iproute|iptables|iputils|irqbalance|iscsi-initiator-utils|jansson|jasper|jbigkit|json-glib|kernel|kexec-tools|keyutils|keyutils-libs|kmod|krb5|krb5-libs|kubernetes|less|libacl|libaio|libarchive|libassuan|libatomic_ops|libblkid|libbsd|libcap|libcap-ng|libcgroup|libcom_err|libcomps|libcroco|libdatrie|libdb|libdrm|libedit|liberation-fonts|libev|libevent|libffi|libgcrypt|libglade2|libglvnd|libgpg-error|libgudev|libICE|libidn|libidn2|libiscsi|libjpeg-turbo|libksba|libldb|libmetalink|libmnl|libmodman|libmount|libndp|libnet|libnetfilter_conntrack|libnetfilter_cthelper|libnetfilter_cttimeout|libnetfilter_queue|libnfnetlink|libnfs|libnfsidmap|libnl3|libpcap|libpciaccess|libpng|libproxy|libpsl|libpwquality|librepo|libreport|libseccomp|libselinux|libsemanage|libsepol|libsigsegv|libSM|libsolv|libsoup|libssh2|libtalloc|libtasn1|libtdb|libtevent|libthai|libtiff|libtirpc|libtomcrypt|libtommath|libtool|libunistring|libunwind|libusb|libusbx|libuser|libutempter|libverto|libX11|libXau|libxcb|libXcomposite|libXcursor|libXdamage|libXext|libXfixes|libXft|libXi|libXinerama|libxml2|libXmu|libXrandr|libXrender|libxshmfence|libxslt|libXt|libXxf86misc|libXxf86vm|libyaml|linux-firmware|logrotate|lttng-ust|lua|lvm2|lz4|lzo|make|mcpp|mdadm|mesa|mokutil|mozjs17|mpfr|nano|ncurses|nettle|net-tools|NetworkManager|newt|nfs-utils|nghttp2|nmap|npth|nspr|nss|nss-pem|nss-softokn|nss-util|numactl|openldap|openssh|openssl|os-prober|ostree|p11-kit|pam|pango|passwd|pciutils|pcre|perl|perl-libs|pixman|policycoreutils|polkit|polkit-pkla-compat|popt|ppp|procps-ng|protobuf-c|publicsuffix-list|pygobject3|pyliblzma|pyserial|python|python3|python-beautifulsoup4|python-cffi|python-chardet|python-configobj|python-crypto|python-cryptography|python-cssselect|python-dateutil|python-decorator|python-dmidecode|python-docker-py|python-docker-pycreds|python-enum34|python-ethtool|python-html5lib|python-idna|python-iniparse|python-ipaddress|python-IPy|python-jinja2|python-jsonpatch|python-jsonpointer|python-lxml|python-markupsafe|python-oauthlib|python-paramiko|python-pip|python-ply|python-prettytable|python-progressbar|python-pyasn1|python-pycparser|python-pycurl|python-pygpgme|python-pysocks|python-pyudev|python-requestbuilder|python-requests|python-rhsm|python-setuptools|python-six|python-slip|python-urlgrabber|python-urllib3|python-websocket-client|pyxattr|PyYAML|qemu|qrencode|quota|readline|rpcbind|rpm|rsync|runc|samba|sed|selinux-policy|setools|setup|sgml-common|shadow-utils|shared-mime-info|shim-signed|skopeo|skopeo-containers|slang|snappy|socat|sqlite|sssd|subscription-manager|sudo|systemd|tar|tcl|tcp_wrappers|tcp_wrappers-libs|texinfo|tk|tmux|tuned|tzdata|usermode|userspace-rcu|ustr|util-linux|vim|virt-what|wayland|which|xfsprogs|xorg-x11-server-utils|xorg-x11-xauth|xorg-x11-xinit|xz|yum|yum-metadata-parser|zlib)";

        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("echo CI_MESSAGE = $CI_MESSAGE"));
        attachTrigger(new CIBuildTrigger(true, Collections.singletonList(
                getSubscriberProviderData("org.fedoraproject.dev.logger.log", null, null, new MsgCheck("$.commit.repo", packages))
        )), jobA);
        // Allow for connection
        Thread.sleep(5000);

        File privateKey = File.createTempFile("ssh", "key");
        FileUtils.copyURLToFile(
                FedmsgRelayContainer.class
                        .getResource("FedmsgRelayContainer/unsafe"), privateKey);
        Files.setPosixFilePermissions(privateKey.toPath(), singleton(OWNER_READ));

        File ssh = File.createTempFile("jenkins", "ssh");
        FileUtils.writeStringToFile(ssh,
                "#!/bin/sh\n" +
                        "exec ssh -o StrictHostKeyChecking=no -i "
                        + privateKey.getAbsolutePath()
                        + " fedmsg2@" + fedmsgRelay.getSshIPAndPort()
                        + " fedmsg-logger "
                        + " \"$@\""
        );
        Files.setPosixFilePermissions(ssh.toPath(),
                new HashSet<>(Arrays.asList(OWNER_READ, OWNER_EXECUTE)));

        System.out.println(FileUtils.readFileToString(ssh));
        ProcessBuilder gitLog1Pb = new ProcessBuilder(ssh.getAbsolutePath(),
                "--message='{\"commit\": "
                        + "{\"branch\": \"f26\", "
                        + " \"repo\": \"erlang\""
                        + "}\n"
                        + "}\'",
                "--json-input"
        );
        String output = stringFrom(logProcessBuilderIssues(gitLog1Pb, "ssh"));
        System.out.println(output);

        FreeStyleBuild lastBuild = jobA.getLastBuild();
        j.assertBuildStatusSuccess(lastBuild);
        j.assertLogContains("erlang", lastBuild);
    }

    @Test
    public void testTriggerWithPipelineComplexCheck() throws Exception {

        String packages = "(acl|atk|atomic|atomic-devmode|attr|audit|erlang|audit-libs|authconfig|avahi|basesystem|bash|bash-completion|bind|bind99|biosdevname|boost|bridge-utils|bwidget|bzip2|ca-certificates|cairo|c-ares|ceph|checkpolicy|chkconfig|chrony|cloud-init|cloud-utils|cockpit|conntrack-tools|container-selinux|coreutils|cpio|cracklib|criu|crypto-policies|cryptsetup|cups|curl|cyrus-sasl|dbus|dbus-glib|dbus-python|dejavu-fonts|deltarpm|device-mapper-libs|device-mapper-multipath|device-mapper-persistent-data|dhcp|diffutils|ding-libs|dmidecode|dnf|dnsmasq|docker|dracut|dracut-network|e2fsprogs|efibootmgr|efivar|elfutils|emacs|etcd|ethtool|euca2ools|expat|fedora-logos|fedora-release|fedora-repos|file|filesystem|findutils|fipscheck|fipscheck-lib|flannel|fontconfig|fontpackages|freetype|fuse|gawk|gc|gcc|gdbm|gdisk|gdk-pixbuf2|GeoIP|GeoIP-GeoLite-data|gettext|glib2|glibc|glib-networking|glusterfs|gmp|gnupg|gnupg2|gnutls|gobject-introspection|gomtree|gperftools|gpgme|gpm|gpm-libs|graphite2|grep|grub2|gsettings-desktop-schemas|gssproxy|guile|gzip|harfbuzz|hawkey|hdparm|hicolor-icon-theme|hostname|http-parser|hwdata|initscripts|ipcalc|iproute|iptables|iputils|irqbalance|iscsi-initiator-utils|jansson|jasper|jbigkit|json-glib|kernel|kexec-tools|keyutils|keyutils-libs|kmod|krb5|krb5-libs|kubernetes|less|libacl|libaio|libarchive|libassuan|libatomic_ops|libblkid|libbsd|libcap|libcap-ng|libcgroup|libcom_err|libcomps|libcroco|libdatrie|libdb|libdrm|libedit|liberation-fonts|libev|libevent|libffi|libgcrypt|libglade2|libglvnd|libgpg-error|libgudev|libICE|libidn|libidn2|libiscsi|libjpeg-turbo|libksba|libldb|libmetalink|libmnl|libmodman|libmount|libndp|libnet|libnetfilter_conntrack|libnetfilter_cthelper|libnetfilter_cttimeout|libnetfilter_queue|libnfnetlink|libnfs|libnfsidmap|libnl3|libpcap|libpciaccess|libpng|libproxy|libpsl|libpwquality|librepo|libreport|libseccomp|libselinux|libsemanage|libsepol|libsigsegv|libSM|libsolv|libsoup|libssh2|libtalloc|libtasn1|libtdb|libtevent|libthai|libtiff|libtirpc|libtomcrypt|libtommath|libtool|libunistring|libunwind|libusb|libusbx|libuser|libutempter|libverto|libX11|libXau|libxcb|libXcomposite|libXcursor|libXdamage|libXext|libXfixes|libXft|libXi|libXinerama|libxml2|libXmu|libXrandr|libXrender|libxshmfence|libxslt|libXt|libXxf86misc|libXxf86vm|libyaml|linux-firmware|logrotate|lttng-ust|lua|lvm2|lz4|lzo|make|mcpp|mdadm|mesa|mokutil|mozjs17|mpfr|nano|ncurses|nettle|net-tools|NetworkManager|newt|nfs-utils|nghttp2|nmap|npth|nspr|nss|nss-pem|nss-softokn|nss-util|numactl|openldap|openssh|openssl|os-prober|ostree|p11-kit|pam|pango|passwd|pciutils|pcre|perl|perl-libs|pixman|policycoreutils|polkit|polkit-pkla-compat|popt|ppp|procps-ng|protobuf-c|publicsuffix-list|pygobject3|pyliblzma|pyserial|python|python3|python-beautifulsoup4|python-cffi|python-chardet|python-configobj|python-crypto|python-cryptography|python-cssselect|python-dateutil|python-decorator|python-dmidecode|python-docker-py|python-docker-pycreds|python-enum34|python-ethtool|python-html5lib|python-idna|python-iniparse|python-ipaddress|python-IPy|python-jinja2|python-jsonpatch|python-jsonpointer|python-lxml|python-markupsafe|python-oauthlib|python-paramiko|python-pip|python-ply|python-prettytable|python-progressbar|python-pyasn1|python-pycparser|python-pycurl|python-pygpgme|python-pysocks|python-pyudev|python-requestbuilder|python-requests|python-rhsm|python-setuptools|python-six|python-slip|python-urlgrabber|python-urllib3|python-websocket-client|pyxattr|PyYAML|qemu|qrencode|quota|readline|rpcbind|rpm|rsync|runc|samba|sed|selinux-policy|setools|setup|sgml-common|shadow-utils|shared-mime-info|shim-signed|skopeo|skopeo-containers|slang|snappy|socat|sqlite|sssd|subscription-manager|sudo|systemd|tar|tcl|tcp_wrappers|tcp_wrappers-libs|texinfo|tk|tmux|tuned|tzdata|usermode|userspace-rcu|ustr|util-linux|vim|virt-what|wayland|which|xfsprogs|xorg-x11-server-utils|xorg-x11-xauth|xorg-x11-xinit|xz|yum|yum-metadata-parser|zlib)";

        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('master') {\n def scott = waitForCIMessage providerName: 'test'," +
                " checks: [[expectedValue: '" + packages + "', field: '$.commit.repo']]," +
                " topic: 'org.fedoraproject.dev.logger.log'" +
                "\necho \"scott = \" + scott}", true));
        wait.scheduleBuild2(0);

        // Allow for connection
        Thread.sleep(5000);

        File privateKey = File.createTempFile("ssh", "key");
        FileUtils.copyURLToFile(
                FedmsgRelayContainer.class
                        .getResource("FedmsgRelayContainer/unsafe"), privateKey);
        Files.setPosixFilePermissions(privateKey.toPath(), singleton(OWNER_READ));

        File ssh = File.createTempFile("jenkins", "ssh");
        FileUtils.writeStringToFile(ssh,
                "#!/bin/sh\n" +
                        "exec ssh -o StrictHostKeyChecking=no -i "
                        + privateKey.getAbsolutePath()
                        + " fedmsg2@" + fedmsgRelay.getSshIPAndPort()
                        + " fedmsg-logger "
                        + " \"$@\""
        );
        Files.setPosixFilePermissions(ssh.toPath(),
                new HashSet<>(Arrays.asList(OWNER_READ, OWNER_EXECUTE)));

        System.out.println(FileUtils.readFileToString(ssh));
        ProcessBuilder gitLog1Pb = new ProcessBuilder(ssh.getAbsolutePath(),
                "--message='{\"commit\": "
                        + "{\"branch\": \"f26\", "
                        + " \"repo\": \"erlang\""
                        + "}\n"
                        + "}\'",
                "--json-input"
        );
        String output = stringFrom(logProcessBuilderIssues(gitLog1Pb, "ssh"));
        System.out.println(output);

        j.assertBuildStatusSuccess(wait.getLastBuild());
        j.assertLogContains("erlang", wait.getLastBuild());
    }

    @Test
    public void testTriggeringUsingFedMsgLogger() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("echo CI_MESSAGE = $CI_MESSAGE"));
        attachTrigger(new CIBuildTrigger(true, Collections.singletonList(new FedMsgSubscriberProviderData(
                "test",
                new MessagingProviderOverrides("org.fedoraproject.dev.logger.log"),
                Collections.singletonList(new MsgCheck("compose", "+compose_id.+message.+")),
                "CI_MESSAGE",
                60
        ))), jobA);
        // Allow for connection
        Thread.sleep(5000);

        File privateKey = File.createTempFile("ssh", "key");
        FileUtils.copyURLToFile(
                FedmsgRelayContainer.class
                        .getResource("FedmsgRelayContainer/unsafe"), privateKey);
        Files.setPosixFilePermissions(privateKey.toPath(), singleton(OWNER_READ));

        File ssh = File.createTempFile("jenkins", "ssh");
        FileUtils.writeStringToFile(ssh,
                "#!/bin/sh\n" +
                        "exec ssh -o StrictHostKeyChecking=no -i "
                        + privateKey.getAbsolutePath()
                        + " fedmsg2@" + fedmsgRelay.getSshIPAndPort()
                        + " fedmsg-logger "
                        + " \"$@\""
        );
        Files.setPosixFilePermissions(ssh.toPath(),
                new HashSet<>(Arrays.asList(OWNER_READ, OWNER_EXECUTE)));

        System.out.println(FileUtils.readFileToString(ssh));
        ProcessBuilder gitLog1Pb = new ProcessBuilder(ssh.getAbsolutePath(),
                "--message='{\"compose\": "
                        + "{\"compose_id\": \"This is a message.\"}}'",
                "--json-input"
        );
        String output = stringFrom(logProcessBuilderIssues(gitLog1Pb,
                "ssh"));
        System.out.println(output);

        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("This is a message", jobA.getLastBuild());
    }

    @Test
    public void testPipelineSendMsgReturnMessage() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "job");
        job.setDefinition(new CpsFlowDefinition("node('master') {\n def message = sendCIMessage " +
                " providerName: 'test', " +
                " messageContent: '', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'\n" +
                " echo message.getMessageId()\necho message.getMessageContent()\n}", true));
        j.buildAndAssertSuccess(job);
        // See https://github.com/jenkinsci/jms-messaging-plugin/issues/125
        // timestamp == 0 indicates timestamp was not set in message
        j.assertBuildStatusSuccess(job.getLastBuild());
    }

    @Test
    public void testPipelineInvalidProvider() throws Exception {
        _testPipelineInvalidProvider();
    }

    @SuppressWarnings("unused")
    private void sendFedMsgMessageUsingLogger(String message) throws Exception {
        File privateKey = File.createTempFile("ssh", "key");
        FileUtils.copyURLToFile(
                FedmsgRelayContainer.class
                        .getResource("FedmsgRelayContainer/unsafe"), privateKey);
        Files.setPosixFilePermissions(privateKey.toPath(), singleton(OWNER_READ));

        File ssh = File.createTempFile("jenkins", "ssh");
        FileUtils.writeStringToFile(ssh,
                "#!/bin/sh\n" +
                        "exec ssh -o StrictHostKeyChecking=no -i "
                        + privateKey.getAbsolutePath()
                        + " fedmsg2@" + fedmsgRelay.getIpAddress()
                        + " fedmsg-logger "
                        + " \"$@\""
        );
        Files.setPosixFilePermissions(ssh.toPath(),
                new HashSet<>(Arrays.asList(OWNER_READ, OWNER_EXECUTE)));

        System.out.println(FileUtils.readFileToString(ssh));
        ProcessBuilder gitLog1Pb = new ProcessBuilder(ssh.getAbsolutePath(),
                "--message='" + message + "'",
//                "--message='{\"compose\": "
//                        + "{\"compose_id\": \"This is a message.\"}}\'",
                "--json-input"
        );
        String output = stringFrom(logProcessBuilderIssues(gitLog1Pb,
                "ssh"));
        System.out.println(output);

    }

}
