package com.j256.testcheckpublisher.lambda.github;

import com.google.gson.annotations.SerializedName;

/**
 * Request for an an access-token:
 * https://docs.github.com/en/rest/reference/apps#create-an-installation-access-token-for-an-app
 * 
 * @author graywatson
 */
public class AccessTokenRequest {

	@SerializedName("installation_id")
	final int installationId;
	final String[] repositories;

	public AccessTokenRequest(int installationId, String[] repositories) {
		this.installationId = installationId;
		this.repositories = repositories;
	}

	public AccessTokenRequest(int installationId, String repository) {
		this.installationId = installationId;
		this.repositories = new String[] { repository };
	}

	public int getInstallationId() {
		return installationId;
	}

	public String[] getRepositories() {
		return repositories;
	}
}
