package com.j256.testcheckpublisher.lambda.github;

/**
 * Installation info response.
 * 
 * @author graywatson
 */
public class InstallationInfo {

	private int id;
	private InstallationInfoAccount account;

	public int getId() {
		return id;
	}

	public InstallationInfoAccount getAccount() {
		return account;
	}

	@Override
	public String toString() {
		return "InstallationInfo [id=" + id + ", account=" + account + "]";
	}

	public static class InstallationInfoAccount {
		String login;

		public String getLogin() {
			return login;
		}

		@Override
		public String toString() {
			return "InstallationInfoAccount [login=" + login + "]";
		}
	}
}