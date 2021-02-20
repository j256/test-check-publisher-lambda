package com.j256.testcheckpublisher.lambda.github;

import com.google.gson.annotations.SerializedName;

/**
 * Response from the get installation request.
 * 
 * https://docs.github.com/en/rest/reference/apps#get-an-installation-for-the-authenticated-app
 * 
 * @author graywatson
 */
public class InstallationResponse {

	private Account account;

	public String getOwner() {
		if (account == null) {
			return null;
		} else {
			return account.getOwner();
		}
	}

	/**
	 * Account associated with the installation.
	 */
	public static class Account {

		@SerializedName("login")
		private String owner;

		public String getOwner() {
			return owner;
		}
	}
}
