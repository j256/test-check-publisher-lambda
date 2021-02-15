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
	 * Find and return the installation-id for a particular repo or -1 on error.
	 */
	public int findInstallationId() throws IOException;

	/**
	 * Login to github meaning get the access token.
	 * 
	 * @return true if worked else false.
	 */
	public boolean login() throws IOException;

	/**
	 * Return information about a commit or null on error.
	 */
	public CommitInfoResponse requestCommitInfo(String topSha)
			throws JsonSyntaxException, UnsupportedOperationException, IOException;

	/**
	 * Return information about file tree or null on error.
	 */
	public Collection<TreeFile> requestTreeFiles(String sha) throws IOException;

	/**
	 * Added check information to github.
	 * 
	 * @return true if worked else false.
	 */
	public boolean addCheckRun(CheckRunRequest request) throws IOException;

	/**
	 * Get the status line from the last request for logging purposes.
	 */
	public StatusLine getLastStatusLine();
}
