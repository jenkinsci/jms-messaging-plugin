package com.redhat.ci.auth;

import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;

public class CIContext extends InitialContext {
	public static final String DESTINATION_NAME = "brewEvents";
	private static final String destination = "destination." + DESTINATION_NAME;

	private CIContext(Properties props) throws NamingException {
		super(props);
	}

	private static CIContext getCIContext (Properties props) throws NamingException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(CIContext.class.getClassLoader());
		CIContext ctx = new CIContext(props);
        Thread.currentThread().setContextClassLoader(cl);
        return ctx;
	}

	public static CIContext getCIContext(String url, String address) throws NamingException {
		Properties props = getProperties(url);
		props.setProperty(destination, address);
		return getCIContext(props);
	}

	private static Properties getProperties(String url) {
		Properties props = new Properties();
		props.setProperty("java.naming.factory.initial", "org.apache.qpid.jndi.PropertiesFileInitialContextFactory");
		props.setProperty("connectionfactory.qpidConnectionfactory", url);
		return props;
	}
}
