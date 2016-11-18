package com.redhat.jenkins.plugins.ci;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.redhat.ci.auth.CIConfiguration;
import com.redhat.ci.auth.CIContext;
import com.redhat.ci.auth.CILoginCallbackHandler;
import com.redhat.ci.auth.CIPrivilegedAction;
import com.redhat.jenkins.plugins.Messages;
import com.redhat.utils.PluginUtils;

public class CICreateTaskRepoBuilder extends Builder {
    private static final Logger log = Logger.getLogger(CICreateTaskRepoBuilder.class.getName());

    private static final String BUILDER_NAME = Messages.CreateTaskRepoBuilder();
    private static final String QUEUE_NAME = "task-repo";

    private static final String DEFAULT_EXCHANGE = "amq.direct";
    private static final String DEFAULT_ADDRESS = DEFAULT_EXCHANGE + "/rcm." + QUEUE_NAME;
    private static final String DEFAULT_PROPFILE = "task-repo.properties";
    private static final String DEFAULT_BROKER = "amqp://guest:guest@jms/test?brokerlist='tcp://qpid.devel.redhat.com:5671?sasl_mechs='GSSAPI'&sasl_protocol='qpidd'&sasl_server='qpid.devel.redhat.com'&ssl='true''";
    private static final String DEFAULT_KEYTAB = "${JENKINS_HOME}/plugins/redhat-ci-plugin/ci-user.keytab";
    private static final String DEFAULT_PRINCIPAL = "CI/ci-user.ci-bus.lab.eng.rdu2.redhat.com@REDHAT.COM";

    private static final Integer TASK_TIMEOUT = 3600 * 2 * 1000;  // 2 hours (3600 seconds/hour, in milliseconds)

    private static final String REPO_VAR_NAME = "TASK_REPO_URLS";
    private static final String REPO_URL = "http://download.eng.bos.redhat.com";
    private static final String REPO_PATH = "/mnt/redhat";  // Base value for returned repo path.  This is stripped and replaced with REPO_URL.

    private String broker;
    private String address;
    private String keytab;
    private String principal;
    private String propfile;
    private String taskIds;
    private Boolean ignore;

    @DataBoundConstructor
    public CICreateTaskRepoBuilder(String broker, String address, String keytab, String principal, String taskIds, String propfile, Boolean ignore) {
        this.broker = broker;
        this.address = address;
        this.keytab = keytab;
        this.principal = principal;
        this.propfile = propfile;
        this.taskIds = taskIds;
        this.ignore = ignore;
    }

    public String getBroker() {
        return broker;
    }

    public void setBroker(final String broker) {
        this.broker = StringUtils.strip(StringUtils.stripToNull(broker), "/");
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getKeytab() {
        return keytab;
    }

    public void setKeytab(String keytab) {
        this.keytab = keytab;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public void setPropfile(String propfile) {
        this.propfile = propfile;
    }

    public String getPropfile() {
        return propfile;
    }

    public String getTaskIds() {
        return taskIds;
    }

    public void setTaskId(String taskIds) {
        this.taskIds = taskIds;
    }

    public Boolean getIgnore() {
        return ignore;
    }

    public void setIgnore(Boolean ignore) {
        this.ignore = ignore;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        Boolean result = true;
        StringBuffer fsb = new StringBuffer();
        EnvVars vars = new EnvVars();
        PrintStream logger = listener.getLogger();

        String taskIds = PluginUtils.getSubstitutedValue(getTaskIds(), build.getEnvironment(listener));
        for (String id : Arrays.asList(taskIds.split("\\s*,\\s*"))) {
            String taskId = PluginUtils.getSubstitutedValue(id, build.getEnvironment(listener));
            log2ConsoleAndLog(logger, Level.INFO, "Create task repo for task id " + taskId + ".");
            Session session = null;
            Connection connection = null;
            try {
                LoginContext lc = new LoginContext(CIConfiguration.CI_APPLICATION_NAME,
                        new Subject(),
                        new CILoginCallbackHandler(),
                        new CIConfiguration(principal, PluginUtils.getSubstitutedValue(keytab, build.getEnvironment(listener))));
                lc.login();
                CIContext context = CIContext.getCIContext(broker, address);
                connection = Subject.doAs(lc.getSubject(), new CIPrivilegedAction(context));
                connection.start();
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

                String uuid =  UUID.randomUUID().toString();
                Destination responseQ = session.createQueue(generateResponseAddress(uuid, QUEUE_NAME));
                MessageConsumer consumer = session.createConsumer(responseQ);

                Destination repoQ = session.createQueue(address);
                MessageProducer producer = session.createProducer(repoQ);
                MapMessage message = session.createMapMessage();
                message.setInt("task_id", Integer.parseInt(taskId));
                message.setJMSReplyTo(responseQ);
                message.setFloat("time", Calendar.getInstance().getTimeInMillis());

                producer.send(message);

                MapMessage response = (MapMessage)consumer.receive(TASK_TIMEOUT);
                if (response == null) {
                    log2ConsoleAndLog(logger, Level.WARNING, "Create task repo build step timed out while waiting for a response.");
                    result = false;
                } else if (!response.getString("status").equals("success")) {
                    log2ConsoleAndLog(logger, Level.WARNING, "Create task repo build step failed:\n" + response.toString());
                    result = false;
                } else {
                    StringBuffer sb = new StringBuffer();
                    for (String s : response.getString("output").split("\n")) {
                        if (s.startsWith("Creating")) {
                            if (sb.length() > 0) {
                                sb.append(";");
                            }
                            String pieces[] = s.split(" ");
                            if (pieces.length > 4) {
                                sb.append(pieces[4].replace(REPO_PATH, REPO_URL));
                            }
                        }
                    }

                    String var = REPO_VAR_NAME + "_" + taskId;
                    vars.put(var, sb.toString());

                    fsb.append(var + "=" + sb.toString() + "\n");

                    if (vars.get(REPO_VAR_NAME) != null) {
                        vars.put(REPO_VAR_NAME, vars.get(REPO_VAR_NAME) + ";" + sb.toString());
                    } else {
                        vars.put(REPO_VAR_NAME, sb.toString());
                    }
                }
            } catch (Exception e) {
                log2ConsoleAndLog(logger, Level.SEVERE, "Unhandled exception in perform.", e);
                result = false;
            } finally {
                if (session != null) {
                    try {
                        session.close();
                    } catch (JMSException e) {
                    }
                }
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (JMSException e) {
                    }
                }
            }
        }

        fsb.append(REPO_VAR_NAME + "=" + vars.get(REPO_VAR_NAME) + "\n");
        FilePath file = build.getWorkspace().child(getPropfile());
        file.write(fsb.toString(), "UTF-8");

        build.addAction(new CIEnvironmentContributingAction(vars));

        return (ignore ? true : result);
    }

    private void log2ConsoleAndLog(PrintStream logger, Level level, String msg) {
        log2ConsoleAndLog(logger, level, msg, null);
    }

    private void log2ConsoleAndLog(PrintStream logger, Level level, String msg, Exception e) {
        log.log(level, msg, e);
        if (e != null) {
            msg += "\n" + e.getMessage() + "\n" + ExceptionUtils.getStackTrace(e);
        }
        logger.println(level.getName() + ": " + msg);

    }

    private static String generateResponseAddress(String uuid, String queueName) {
        StringBuffer sb = new StringBuffer();
        sb.append("tmp." + queueName + "-client-" + uuid + ";\n");
        sb.append("    { create: receiver,\n");
        sb.append("      assert: receiver,\n");
        sb.append("        node: {      type: queue,\n");
        sb.append("                  durable: false,\n");
        sb.append("                x-declare: {   exclusive: true,\n");
        sb.append("                             auto-delete: true,\n");
        sb.append("                               arguments: { \"qpid.policy_type\": ring,\n");
        sb.append("                                               \"qpid.max_size\": 1048576,\n");
        sb.append("                                              \"qpid.max_count\": 1024 } } },\n");
        sb.append("        link: { x-bindings: [ { exchange: \"amq.direct\",\n");
        sb.append("                                     key: \"tmp." + queueName + "-client-" + uuid + "\",\n");
        sb.append("                                   queue: \"tmp." + queueName + "-client-" + uuid + "\" } ] } }\n");

        return sb.toString();
    }

    @Override
    public CICreateTaskRepoBuilderDescriptor getDescriptor() {
        return (CICreateTaskRepoBuilderDescriptor) Jenkins.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static class CICreateTaskRepoBuilderDescriptor extends BuildStepDescriptor<Builder> {

        public String getDisplayName() {
            return BUILDER_NAME;
        }

        public String getDefaultBroker() {
            return DEFAULT_BROKER;
        }

        public String getDefaultAddress() {
            return DEFAULT_ADDRESS;
        }

        public String getDefaultKeytab() {
            return DEFAULT_KEYTAB;
        }

        public String getDefaultPrincipal() {
            return DEFAULT_PRINCIPAL;
        }

        public String getDefaultPropfile() {
            return DEFAULT_PROPFILE;
        }

        @Override
        public CICreateTaskRepoBuilder newInstance(StaplerRequest sr, JSONObject jo) {
            return new CICreateTaskRepoBuilder(jo.getString("broker"), jo.getString("address"), jo.getString("keytab"),
                                               jo.getString("principal"), jo.getString("taskIds"), jo.getString("propfile"),
                                               jo.getBoolean("ignore"));
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest sr, JSONObject formData) throws FormException {
            save();
            return super.configure(sr, formData);
        }

        public FormValidation doCheckTaskIds(@QueryParameter String value) throws IOException, ServletException {
            if (value != null && !value.trim().equals("")) {
                return FormValidation.ok();
            }
            return FormValidation.error("Please enter a comma-separated list of task IDs or an environment variable for the task IDs.");
        }

        public FormValidation doTestConnection(@QueryParameter("broker") String broker,
                @QueryParameter("address") String address,
                @QueryParameter("keytab") String keytab,
                @QueryParameter("principal") String principal) throws ServletException {
            broker = StringUtils.strip(StringUtils.stripToNull(broker), "/");
            if (broker != null && isValidURL(broker)) {
                try {
                    if (testConnection(broker, address, keytab, principal)) {
                        return FormValidation.ok(Messages.Success());
                    }
                } catch (LoginException e) {
                    log.log(Level.SEVERE, "Login Exception.", e);
                    return FormValidation.error(Messages.AuthFailure());
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Unhandled exception in doTestConnection: ", e);
                    return FormValidation.error(Messages.Error() + ": " + e);
                }
            }
            return FormValidation.error(Messages.InvalidURI());
        }

        private boolean testConnection(String broker, String address, String keytab, String principal) throws Exception {
            broker = StringUtils.strip(StringUtils.stripToNull(broker), "/");
            if (broker != null && isValidURL(broker)) {
                EnvVars vars = new EnvVars();
                vars.put("JENKINS_HOME", Jenkins.getInstance().getRootDir().toString());
                LoginContext lc = new LoginContext(CIConfiguration.CI_APPLICATION_NAME,
                        new Subject(),
                        new CILoginCallbackHandler(),
                        new CIConfiguration(principal, PluginUtils.getSubstitutedValue(keytab, vars)));
                lc.login();
                CIContext context = CIContext.getCIContext(broker, address);
                Connection connection = Subject.doAs(lc.getSubject(), new CIPrivilegedAction(context));
                connection.start();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                session.close();
                connection.close();
                return true;
            }
            log.severe("Failed in testConnection");
            return false;

        }

        private boolean isValidURL(String url) {
            try {
                new URI(url);
            } catch (URISyntaxException e) {
                log.log(Level.SEVERE, "URISyntaxException, returning false.");
                return false;
            }
            return true;
        }
    }
}
