package com.redhat.jenkins.plugins.ci.integration;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.util.Collections.singleton;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;

import com.redhat.jenkins.plugins.ci.integration.po.CINotifierPostBuildStep;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.junit.WithDocker;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.inject.Inject;
import com.redhat.jenkins.plugins.ci.integration.docker.fixtures.FedmsgRelayContainer;
import com.redhat.jenkins.plugins.ci.integration.po.CIEventTrigger;
import com.redhat.jenkins.plugins.ci.integration.po.FedMsgMessagingProvider;
import com.redhat.jenkins.plugins.ci.integration.po.GlobalCIConfiguration;

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
@WithPlugins("jms-messaging")
@WithDocker
public class FedMsgMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {
    @Inject private DockerContainerHolder<FedmsgRelayContainer> docker;

    private FedmsgRelayContainer fedmsgRelay = null;

    @Test
    public void testGlobalConfigTestConnection() throws Exception {
    }

    @Test
    public void testAddDuplicateMessageProvider() throws Exception {
        jenkins.configure();
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        FedMsgMessagingProvider msgConfig = new FedMsgMessagingProvider(ciPluginConfig).addMessagingProvider();
        msgConfig.name("test")
                .topic("tom")
                .hubAddr("tcp://127.0.0.1:4001")
                .pubAddr("tcp://127.0.0.1:2003");
        _testAddDuplicateMessageProvider();
    }

    @Test
    public void testSimpleCIEventSubscribe() throws Exception {
        _testSimpleCIEventSubscribe();
    }

    @Test
    public void testSimpleCIEventSubscribeWithTopicOverride() throws Exception, InterruptedException {
        _testSimpleCIEventSubscribeWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventSubscribeWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventSubscribeWithTopicOverrideAndVariableTopic();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithPipelineSendMsg() throws Exception {
        _testSimpleCIEventTriggerWithPipelineSendMsg();
    }

    @Test
    public void testSimpleCIEventTrigger() throws Exception {
        _testSimpleCIEventTrigger();
    }

    @Test
    public void testSimpleCIEventTriggerWithWildcardInSelector() throws Exception {
        _testSimpleCIEventTriggerWithWildcardInSelector();
    }

    @Test
    public void testSimpleCIEventTriggerWithRegExpCheck() throws Exception {
        _testSimpleCIEventTriggerWithRegExpCheck();
    }

    @Test
    public void testSimpleCIEventTriggerWithTopicOverride() throws Exception {
        _testSimpleCIEventTriggerWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventTriggerWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventTriggerWithTopicOverrideAndVariableTopic();
    }

    @Test
    public void testSimpleCIEventTriggerWithParamOverride() throws Exception {
        _testSimpleCIEventTriggerWithParamOverride();
    }

    @Test
    public void testSimpleCIEventTriggerHeadersInEnv() throws Exception, InterruptedException {
        FreeStyleJob jobB = jenkins.jobs.create();
        String expected = "{\"topic\":\"org.fedoraproject\"}";
        _testSimpleCIEventTriggerHeadersInEnv(jobB, expected);
    }

    @Test
    public void testSimpleCIEventSubscribeWithNoParamOverride() throws Exception, InterruptedException {
        _testSimpleCIEventSubscribeWithNoParamOverride();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerOnPipelineJob() throws Exception {
        _testSimpleCIEventTriggerOnPipelineJob();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithPipelineWaitForMsg() throws Exception {
        _testSimpleCIEventTriggerWithPipelineWaitForMsg();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventSendAndWaitPipeline() throws Exception {
        WorkflowJob send = jenkins.jobs.create(WorkflowJob.class);
        String expected = "scott = {\"CI_TYPE\":\"code-quality-checks-done\"," +
                "\"message-content\":\"abcdefg\",\"CI_STATUS\":\"failed\",\"CI_NAME\":\"" +
                send.name +
                "\",\"topic\":\"org.fedoraproject\"}";
        _testSimpleCIEventSendAndWaitPipeline(send, expected);
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventSendAndWaitPipelineWithVariableTopic() throws Exception {
        WorkflowJob send = jenkins.jobs.create(WorkflowJob.class);
        String expected = "scott = {\"CI_TYPE\":\"code-quality-checks-done\"" +
                ",\"message-content\":\"abcdefg\",\"CI_STATUS\":\"failed\",\"CI_NAME\":\"" + send.name +
                "\",\"topic\":\"org.fedoraproject.my-topic\"}";
        String selector = "topic = '";
        _testSimpleCIEventSendAndWaitPipelineWithVariableTopic(send, selector, expected);
    }

    @Test
    public void testJobRename() throws Exception {
        _testJobRename();
    }

    @Test
    public void testDisabledJobDoesNotGetTriggered() throws Exception {
        _testDisabledJobDoesNotGetTriggered();
    }

    @Ignore("failonError does not work in FedMsg/ZMQ")
    @Test
    public void testEnsureFailedSendingOfMessageFailsBuild() throws Exception {
        // failonError does not work in FedMsg
    }

    @Ignore("failonError does not work in FedMsg/ZMQ")
    @Test
    public void testEnsureFailedSendingOfMessageFailsPipelineBuild() throws Exception {
    }

    @WithPlugins({"workflow-aggregator", "monitoring"})
    @Test
    public void testAbortWaitingForMessageWithPipelineBuild() throws Exception {
        _testAbortWaitingForMessageWithPipelineBuild();
    }

    @Before
    public void setUp() throws Exception {
        fedmsgRelay = docker.get();
        jenkins.configure();
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        FedMsgMessagingProvider msgConfig = new FedMsgMessagingProvider(ciPluginConfig).addMessagingProvider();
        msgConfig.name("test")
                .topic("org.fedoraproject")
                .hubAddr(fedmsgRelay.getHub())
                .pubAddr(fedmsgRelay.getPublisher());
        jenkins.save();
    }

    @Test
    public void testTriggeringUsingFedMsgLoggerAndSingleQuotes() throws Exception {
        testTriggeringUsingFedMsgLogger("topic = 'org.fedoraproject.dev.logger.log'");
    }

    @Test
    public void testTriggeringUsingFedMsgLoggerAndDoubleQuotes() throws Exception {
        testTriggeringUsingFedMsgLogger("topic = \"org.fedoraproject.dev.logger.log\"");
    }

    @Test
    public void testTriggerWithComplexCheck() throws Exception {

        String packages = "(acl|atk|atomic|atomic-devmode|attr|audit|erlang|audit-libs|authconfig|avahi|basesystem|bash|bash-completion|bind|bind99|biosdevname|boost|bridge-utils|bwidget|bzip2|ca-certificates|cairo|c-ares|ceph|checkpolicy|chkconfig|chrony|cloud-init|cloud-utils|cockpit|conntrack-tools|container-selinux|coreutils|cpio|cracklib|criu|crypto-policies|cryptsetup|cups|curl|cyrus-sasl|dbus|dbus-glib|dbus-python|dejavu-fonts|deltarpm|device-mapper-libs|device-mapper-multipath|device-mapper-persistent-data|dhcp|diffutils|ding-libs|dmidecode|dnf|dnsmasq|docker|dracut|dracut-network|e2fsprogs|efibootmgr|efivar|elfutils|emacs|etcd|ethtool|euca2ools|expat|fedora-logos|fedora-release|fedora-repos|file|filesystem|findutils|fipscheck|fipscheck-lib|flannel|fontconfig|fontpackages|freetype|fuse|gawk|gc|gcc|gdbm|gdisk|gdk-pixbuf2|GeoIP|GeoIP-GeoLite-data|gettext|glib2|glibc|glib-networking|glusterfs|gmp|gnupg|gnupg2|gnutls|gobject-introspection|gomtree|gperftools|gpgme|gpm|gpm-libs|graphite2|grep|grub2|gsettings-desktop-schemas|gssproxy|guile|gzip|harfbuzz|hawkey|hdparm|hicolor-icon-theme|hostname|http-parser|hwdata|initscripts|ipcalc|iproute|iptables|iputils|irqbalance|iscsi-initiator-utils|jansson|jasper|jbigkit|json-glib|kernel|kexec-tools|keyutils|keyutils-libs|kmod|krb5|krb5-libs|kubernetes|less|libacl|libaio|libarchive|libassuan|libatomic_ops|libblkid|libbsd|libcap|libcap-ng|libcgroup|libcom_err|libcomps|libcroco|libdatrie|libdb|libdrm|libedit|liberation-fonts|libev|libevent|libffi|libgcrypt|libglade2|libglvnd|libgpg-error|libgudev|libICE|libidn|libidn2|libiscsi|libjpeg-turbo|libksba|libldb|libmetalink|libmnl|libmodman|libmount|libndp|libnet|libnetfilter_conntrack|libnetfilter_cthelper|libnetfilter_cttimeout|libnetfilter_queue|libnfnetlink|libnfs|libnfsidmap|libnl3|libpcap|libpciaccess|libpng|libproxy|libpsl|libpwquality|librepo|libreport|libseccomp|libselinux|libsemanage|libsepol|libsigsegv|libSM|libsolv|libsoup|libssh2|libtalloc|libtasn1|libtdb|libtevent|libthai|libtiff|libtirpc|libtomcrypt|libtommath|libtool|libunistring|libunwind|libusb|libusbx|libuser|libutempter|libverto|libX11|libXau|libxcb|libXcomposite|libXcursor|libXdamage|libXext|libXfixes|libXft|libXi|libXinerama|libxml2|libXmu|libXrandr|libXrender|libxshmfence|libxslt|libXt|libXxf86misc|libXxf86vm|libyaml|linux-firmware|logrotate|lttng-ust|lua|lvm2|lz4|lzo|make|mcpp|mdadm|mesa|mokutil|mozjs17|mpfr|nano|ncurses|nettle|net-tools|NetworkManager|newt|nfs-utils|nghttp2|nmap|npth|nspr|nss|nss-pem|nss-softokn|nss-util|numactl|openldap|openssh|openssl|os-prober|ostree|p11-kit|pam|pango|passwd|pciutils|pcre|perl|perl-libs|pixman|policycoreutils|polkit|polkit-pkla-compat|popt|ppp|procps-ng|protobuf-c|publicsuffix-list|pygobject3|pyliblzma|pyserial|python|python3|python-beautifulsoup4|python-cffi|python-chardet|python-configobj|python-crypto|python-cryptography|python-cssselect|python-dateutil|python-decorator|python-dmidecode|python-docker-py|python-docker-pycreds|python-enum34|python-ethtool|python-html5lib|python-idna|python-iniparse|python-ipaddress|python-IPy|python-jinja2|python-jsonpatch|python-jsonpointer|python-lxml|python-markupsafe|python-oauthlib|python-paramiko|python-pip|python-ply|python-prettytable|python-progressbar|python-pyasn1|python-pycparser|python-pycurl|python-pygpgme|python-pysocks|python-pyudev|python-requestbuilder|python-requests|python-rhsm|python-setuptools|python-six|python-slip|python-urlgrabber|python-urllib3|python-websocket-client|pyxattr|PyYAML|qemu|qrencode|quota|readline|rpcbind|rpm|rsync|runc|samba|sed|selinux-policy|setools|setup|sgml-common|shadow-utils|shared-mime-info|shim-signed|skopeo|skopeo-containers|slang|snappy|socat|sqlite|sssd|subscription-manager|sudo|systemd|tar|tcl|tcp_wrappers|tcp_wrappers-libs|texinfo|tk|tmux|tuned|tzdata|usermode|userspace-rcu|ustr|util-linux|vim|virt-what|wayland|which|xfsprogs|xorg-x11-server-utils|xorg-x11-xauth|xorg-x11-xinit|xz|yum|yum-metadata-parser|zlib)";

        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_MESSAGE = $CI_MESSAGE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set("topic = 'org.fedoraproject.dev.logger.log'");
        CIEventTrigger.MsgCheck repoCheck = ciEvent.addMsgCheck();
        repoCheck.expectedValue.set(packages);
        repoCheck.field.set("$.commit.repo");
        jobA.save();
        // Allow for connection
        elasticSleep(5000);

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
                "--message='{\"commit\": "
                        + "{\"branch\": \"f26\", "
                        + " \"repo\": \"erlang\""
                        + "}\n"
                        + "}\'",
                "--json-input"
        );
        String output = stringFrom(logProcessBuilderIssues(gitLog1Pb,
                "ssh"));
        System.out.println(output);

        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("erlang"));

    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testTriggerWithPipelineComplexCheck() throws Exception {

        String packages = "(acl|atk|atomic|atomic-devmode|attr|audit|erlang|audit-libs|authconfig|avahi|basesystem|bash|bash-completion|bind|bind99|biosdevname|boost|bridge-utils|bwidget|bzip2|ca-certificates|cairo|c-ares|ceph|checkpolicy|chkconfig|chrony|cloud-init|cloud-utils|cockpit|conntrack-tools|container-selinux|coreutils|cpio|cracklib|criu|crypto-policies|cryptsetup|cups|curl|cyrus-sasl|dbus|dbus-glib|dbus-python|dejavu-fonts|deltarpm|device-mapper-libs|device-mapper-multipath|device-mapper-persistent-data|dhcp|diffutils|ding-libs|dmidecode|dnf|dnsmasq|docker|dracut|dracut-network|e2fsprogs|efibootmgr|efivar|elfutils|emacs|etcd|ethtool|euca2ools|expat|fedora-logos|fedora-release|fedora-repos|file|filesystem|findutils|fipscheck|fipscheck-lib|flannel|fontconfig|fontpackages|freetype|fuse|gawk|gc|gcc|gdbm|gdisk|gdk-pixbuf2|GeoIP|GeoIP-GeoLite-data|gettext|glib2|glibc|glib-networking|glusterfs|gmp|gnupg|gnupg2|gnutls|gobject-introspection|gomtree|gperftools|gpgme|gpm|gpm-libs|graphite2|grep|grub2|gsettings-desktop-schemas|gssproxy|guile|gzip|harfbuzz|hawkey|hdparm|hicolor-icon-theme|hostname|http-parser|hwdata|initscripts|ipcalc|iproute|iptables|iputils|irqbalance|iscsi-initiator-utils|jansson|jasper|jbigkit|json-glib|kernel|kexec-tools|keyutils|keyutils-libs|kmod|krb5|krb5-libs|kubernetes|less|libacl|libaio|libarchive|libassuan|libatomic_ops|libblkid|libbsd|libcap|libcap-ng|libcgroup|libcom_err|libcomps|libcroco|libdatrie|libdb|libdrm|libedit|liberation-fonts|libev|libevent|libffi|libgcrypt|libglade2|libglvnd|libgpg-error|libgudev|libICE|libidn|libidn2|libiscsi|libjpeg-turbo|libksba|libldb|libmetalink|libmnl|libmodman|libmount|libndp|libnet|libnetfilter_conntrack|libnetfilter_cthelper|libnetfilter_cttimeout|libnetfilter_queue|libnfnetlink|libnfs|libnfsidmap|libnl3|libpcap|libpciaccess|libpng|libproxy|libpsl|libpwquality|librepo|libreport|libseccomp|libselinux|libsemanage|libsepol|libsigsegv|libSM|libsolv|libsoup|libssh2|libtalloc|libtasn1|libtdb|libtevent|libthai|libtiff|libtirpc|libtomcrypt|libtommath|libtool|libunistring|libunwind|libusb|libusbx|libuser|libutempter|libverto|libX11|libXau|libxcb|libXcomposite|libXcursor|libXdamage|libXext|libXfixes|libXft|libXi|libXinerama|libxml2|libXmu|libXrandr|libXrender|libxshmfence|libxslt|libXt|libXxf86misc|libXxf86vm|libyaml|linux-firmware|logrotate|lttng-ust|lua|lvm2|lz4|lzo|make|mcpp|mdadm|mesa|mokutil|mozjs17|mpfr|nano|ncurses|nettle|net-tools|NetworkManager|newt|nfs-utils|nghttp2|nmap|npth|nspr|nss|nss-pem|nss-softokn|nss-util|numactl|openldap|openssh|openssl|os-prober|ostree|p11-kit|pam|pango|passwd|pciutils|pcre|perl|perl-libs|pixman|policycoreutils|polkit|polkit-pkla-compat|popt|ppp|procps-ng|protobuf-c|publicsuffix-list|pygobject3|pyliblzma|pyserial|python|python3|python-beautifulsoup4|python-cffi|python-chardet|python-configobj|python-crypto|python-cryptography|python-cssselect|python-dateutil|python-decorator|python-dmidecode|python-docker-py|python-docker-pycreds|python-enum34|python-ethtool|python-html5lib|python-idna|python-iniparse|python-ipaddress|python-IPy|python-jinja2|python-jsonpatch|python-jsonpointer|python-lxml|python-markupsafe|python-oauthlib|python-paramiko|python-pip|python-ply|python-prettytable|python-progressbar|python-pyasn1|python-pycparser|python-pycurl|python-pygpgme|python-pysocks|python-pyudev|python-requestbuilder|python-requests|python-rhsm|python-setuptools|python-six|python-slip|python-urlgrabber|python-urllib3|python-websocket-client|pyxattr|PyYAML|qemu|qrencode|quota|readline|rpcbind|rpm|rsync|runc|samba|sed|selinux-policy|setools|setup|sgml-common|shadow-utils|shared-mime-info|shim-signed|skopeo|skopeo-containers|slang|snappy|socat|sqlite|sssd|subscription-manager|sudo|systemd|tar|tcl|tcp_wrappers|tcp_wrappers-libs|texinfo|tk|tmux|tuned|tzdata|usermode|userspace-rcu|ustr|util-linux|vim|virt-what|wayland|which|xfsprogs|xorg-x11-server-utils|xorg-x11-xauth|xorg-x11-xinit|xz|yum|yum-metadata-parser|zlib)";

        WorkflowJob wait = jenkins.jobs.create(WorkflowJob.class);
        wait.script.set("node('master') {\n def scott = waitForCIMessage providerName: 'test'," +
                "selector: " +
                " \"topic = 'org.fedoraproject.dev.logger.log'\",  " +
                " checks: [[expectedValue: '" + packages +"', field: '$.commit.repo']]," +
                " topic: 'org.fedoraproject'" +
                "\necho \"scott = \" + scott}");
        wait.save();
        wait.startBuild();

        // Allow for connection
        elasticSleep(5000);

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
                "--message='{\"commit\": "
                        + "{\"branch\": \"f26\", "
                        + " \"repo\": \"erlang\""
                        + "}\n"
                        + "}\'",
                "--json-input"
        );
        String output = stringFrom(logProcessBuilderIssues(gitLog1Pb,
                "ssh"));
        System.out.println(output);

        wait.getLastBuild().shouldSucceed().shouldExist();
        assertThat(wait.getLastBuild().getConsole(), containsString("erlang"));

    }

    @Test
    public void testSimpleCIEventTriggerWithCheck() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        jobA.addShellStep("echo CI_MESSAGE = $CI_MESSAGE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        CIEventTrigger.MsgCheck check = ciEvent.addMsgCheck();
        check.expectedValue.set("Catch me");
        check.field.set(JMSMessagingWorker.MESSAGECONTENTFIELD);
        jobA.save();
        // Allow for connection
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.messageContent.set("Catch me");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
    }

    public void testTriggeringUsingFedMsgLogger(String topic) throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_MESSAGE = $CI_MESSAGE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set(topic);
        CIEventTrigger.MsgCheck check = ciEvent.addMsgCheck();
        check.expectedValue.set(".+compose_id.+message.+");
        check.field.set("compose");
        jobA.save();
        // Allow for connection
        elasticSleep(5000);

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
                "--message='{\"compose\": "
                        + "{\"compose_id\": \"This is a message.\"}}\'",
                "--json-input"
        );
        String output = stringFrom(logProcessBuilderIssues(gitLog1Pb,
                "ssh"));
        System.out.println(output);

        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("This is a message"));
    }

    @WithPlugins({"workflow-aggregator", "monitoring", "dumpling"})
    @Test
    public void testPipelineJobProperties() throws Exception {
        WorkflowJob workflowJob = jenkins.jobs.create(WorkflowJob.class);
        workflowJob.script.set("properties(\n" +
                "        [\n" +
                "                pipelineTriggers(\n" +
                "  [[$class: 'CIBuildTrigger', checks: [], providerName: 'test', selector: 'topic = \"org.fedoraproject.dev.logger.log\"']]\n" +
                "                )\n" +
                "        ]\n" +
                ")\nnode('master') {\n sleep 1\n}");
        workflowJob.save();
        workflowJob.startBuild();
        workflowJob.configure();
        workflowJob.save();
        workflowJob.startBuild();
        elasticSleep(2000);
        printThreadsWithName("iothread-");
        printThreadsWithName("CIBuildTrigger");
        int ioCount = getCurrentThreadCountForName("iothread-");
        assertTrue("iothread-2 count is not 1", ioCount == 1);
        int triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertTrue("CIBuildTrigger count is not 1", triggers == 1);

        System.out.println("Starting rapid fire - no property changes.");
        String message = "{ \"CI_STATUS\": \"failed\" }";
        for (int i = 1 ; i <= 3 ; i++) {
            sendFedMsgMessageUsingLogger(message);
        }
        System.out.println("DONE rapid fire - no property changes.");

        elasticSleep(2000);
        assertTrue("there are not 5 builds", workflowJob.getLastBuild().getNumber() == 5);

        ioCount = getCurrentThreadCountForName("iothread-");
        assertTrue("iothread-2 count is not 1", ioCount == 1);
        triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertTrue("CIBuildTrigger count is not 1", triggers == 1);

        workflowJob.configure();
        workflowJob.script.set("def d = new Double(Math.random())\n" +
                "def r = d + 1.0\n" +
                "\n" +
                "properties(\n" +
                "        [\n" +
                "                pipelineTriggers(\n" +
                "  [[$class: 'CIBuildTrigger', checks: [], providerName: 'test', selector: 'CI_STATUS between 0 and ' + r.toString() + '\\'']]\n" +
                "                )\n" +
                "        ]\n" +
                ")\n" +
                "\n" +
                "node() {\n" +
                "    echo \"hi\"\n" +
                "}");
        workflowJob.sandbox.check(false);
        workflowJob.save();
        workflowJob.startBuild();
        elasticSleep(5000);

        Double randomStatus = Math.random();
        String message2 = "{ \"CI_STATUS\": " + randomStatus.toString() +" }";
        System.out.println("Starting rapid fire - WITH property changes.");
        for (int i = 1 ; i <= 3 ; i++) {
            sendFedMsgMessageUsingLogger(message2);
            elasticSleep(1000);
        }

        System.out.println("DONE rapid fire - WITH property changes.");
        elasticSleep(5000);
        assertTrue("there are not 9 builds", workflowJob.getLastBuild().getNumber() == 9);

        for (int i = 1 ; i <= 9 ; i++) {
            Build b1 = new Build(workflowJob, i);
            assertTrue(b1.isSuccess());
        }
        printThreadsWithName("iothread-");
        printThreadsWithName("CIBuildTrigger");
        ioCount = getCurrentThreadCountForName("iothread-");
        assertTrue("iothread-2 count is not 1", ioCount == 1);
        triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertTrue("CIBuildTrigger count is not 1", triggers == 1);
    }

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
                "--message=\'" + message + "\'",
//                "--message='{\"compose\": "
//                        + "{\"compose_id\": \"This is a message.\"}}\'",
                "--json-input"
        );
        String output = stringFrom(logProcessBuilderIssues(gitLog1Pb,
                "ssh"));
        System.out.println(output);

    }
}
