package com.redhat.ci.auth;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.naming.InitialContext;

public class CIPrivilegedAction implements java.security.PrivilegedAction<Connection> {
    private static final Logger LOGGER = Logger.getLogger(CIPrivilegedAction.class.getName());
	private InitialContext context;

	public CIPrivilegedAction(InitialContext context) {
		this.context = context;
	}

	public Connection run() {
		Connection connection = null;
		try {
			ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup("qpidConnectionfactory");
			connection = connectionFactory.createConnection();
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.log(Level.SEVERE, "Unhandled exception in CIPrivilegedAction.run:", e);
		}
		return connection;
	}
}
