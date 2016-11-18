package com.redhat.ci.auth;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

public class CILoginCallbackHandler implements CallbackHandler {

	public CILoginCallbackHandler() {
		super();
	}

	public CILoginCallbackHandler(String name, String password) {
		super();
		this.username = name;
		this.password = password;
	}

	public CILoginCallbackHandler(String password) {
		super();
		this.password = password;
	}

	private String password;
	private String username;

	public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

		for (int i=0; i<callbacks.length; i++) {
			if (callbacks[i] instanceof NameCallback && username != null) {
				NameCallback nc = (NameCallback) callbacks[i];
				nc.setName(username);
			} else if (callbacks[i] instanceof PasswordCallback) {
				PasswordCallback pc = (PasswordCallback) callbacks[i];
				pc.setPassword(password.toCharArray());
			} else {
				throw new UnsupportedCallbackException(callbacks[i], "Unrecognized Callback");
			}
		}
	}
}
