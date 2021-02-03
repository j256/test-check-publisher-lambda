package com.j256.testcheckpublisher.lambda.github;

import java.io.IOException;
import java.util.Collection;

import org.apache.http.StatusLine;

import com.google.gson.JsonSyntaxException;
import com.j256.testcheckpublisher.lambda.github.TreeInfoResponse.TreeFile;

/**
 * Github client which handles the access and bearer token.
 * 
 * @author graywatson
 */
public interface GithubClient {

	/**
	 * Find and return the installation-id for a particular repo.
	 */
	public int findInstallationId() throws IOException;

	/**
	 * Login to github meaning get the access token.
	 */
	public boolean login() throws IOException;

	/**
	 * Get information about a commit.
	 */
	public CommitInfoResponse requestCommitInfo(String topSha)
			throws JsonSyntaxException, UnsupportedOperationException, IOException;

	/**
	 * Request information about file tree.
	 */
	public Collection<TreeFile> requestTreeFiles(String sha) throws IOException;

	/**
	 * Added check information to github.
	 */
	public boolean addCheckRun(CheckRunRequest request) throws IOException;

	/**
	 * Get the status line from the last request for logging purposes.
	 */
	public StatusLine getLastStatusLine();
}
