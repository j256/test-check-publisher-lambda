package com.j256.testcheckpublisher;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.j256.testcheckpublisher.github.AccessTokenRequest;
import com.j256.testcheckpublisher.github.AccessTokensResponse;
import com.j256.testcheckpublisher.github.CheckRunRequest;
import com.j256.testcheckpublisher.github.CommitInfoResponse;
import com.j256.testcheckpublisher.github.InstallationInfo;
import com.j256.testcheckpublisher.github.TreeFile;
import com.j256.testcheckpublisher.github.TreeInfoResponse;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * Main lambda handler.
 * 
 * @author graywatson
 */
public class GithubClient {

	private static final String JWT_ISSUER_ENV_NAME = "jwt_issuer";
	private static final long JWT_TTL_MILLIS = 10 * 60 * 1000;
	private static final String TREE_TYPE = "tree";

	private final CloseableHttpClient httpclient;
	private final String owner;
	private final String repository;
	private final PrivateKey applicationKey;
	private final String jwtIssuer;

	private final Gson gson = new Gson();
	private int installationId;
	private String bearerToken;
	private String accessToken;

	public GithubClient(CloseableHttpClient httpclient, String owner, String repository, PrivateKey applicationKey) {
		this.httpclient = httpclient;
		this.owner = owner;
		this.repository = repository;
		this.applicationKey = applicationKey;

		this.jwtIssuer = System.getenv(JWT_ISSUER_ENV_NAME);
		if (jwtIssuer == null) {
			throw new IllegalStateException("Could not find JWT issuer env variable");
		}
	}

	public int findInstallationId() throws IOException {

		if (installationId != 0) {
			return installationId;
		}

		// XXX:need to cache this
		// XXX: need to handle paging and request 100 per

		HttpGet get = new HttpGet("https://api.github.com/app/installations");
		get.addHeader("Authorization", "Bearer " + getBearerToken());
		get.addHeader("Accept", "application/vnd.github.v3+json");
		try (CloseableHttpResponse response = httpclient.execute(get)) {

			InstallationInfo[] installations =
					gson.fromJson(new InputStreamReader(response.getEntity().getContent()), InstallationInfo[].class);
			// System.out.println("Installations: " + Arrays.toString(result));

			for (InstallationInfo installation : installations) {
				if (owner.equals(installation.getAccount().getLogin())) {
					installationId = installation.getId();
					return installationId;
				}
			}
		}

		// need to return an exception

		return 0;
	}

	/**
	 * Get information about a commit.
	 */
	public CommitInfoResponse requestCommitInfo(String topSha)
			throws JsonSyntaxException, UnsupportedOperationException, IOException {

		HttpGet get = new HttpGet("https://api.github.com/repos/" + owner + "/" + repository + "/commits/" + topSha);
		get.addHeader("Accept", "application/vnd.github.v3+json");

		try (CloseableHttpResponse response = httpclient.execute(get);
				Reader contentReader = new InputStreamReader(response.getEntity().getContent());) {
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				return gson.fromJson(contentReader, CommitInfoResponse.class);
			} else {
				return null;
			}
		}
	}

	/**
	 * Request information about file tree.
	 */
	public Collection<FileInfo> requestTreeFiles(String sha) throws IOException {

		// GET /repos/{owner}/{repo}/git/trees/{tree_sha}
		HttpGet get = new HttpGet(
				"https://api.github.com/repos/" + owner + "/" + repository + "/git/trees/" + sha + "?recursive=1");
		get.addHeader("Authorization", "token " + getAccessToken());
		get.addHeader("Accept", "application/vnd.github.v3+json");

		List<FileInfo> fileInfos = new ArrayList<>();
		try (CloseableHttpResponse response = httpclient.execute(get);
				Reader reader = new InputStreamReader(response.getEntity().getContent());) {

			// did the request work?
			if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
				return null;
			}

			TreeInfoResponse treeInfoResponse = gson.fromJson(reader, TreeInfoResponse.class);
			if (treeInfoResponse.getTreeFiles() != null) {
				for (TreeFile treeFile : treeInfoResponse.getTreeFiles()) {
					if (!TREE_TYPE.equals(treeFile.getType())) {
						// make our path a relative path from root
						fileInfos.add(new FileInfo(treeFile.getPath(), treeFile.getSha()));
					}
				}
			}
		}
		return fileInfos;
	}

	/**
	 * Added check information to github.
	 */
	public boolean addCheckRun(CheckRunRequest request) throws IOException {

		HttpPost post = new HttpPost("https://api.github.com/repos/" + owner + "/" + repository + "/check-runs");
		post.addHeader("Authorization", "token " + getAccessToken());
		post.addHeader("Accept", "application/vnd.github.v3+json");

		post.setEntity(new StringEntity(gson.toJson(request)));

		try (CloseableHttpResponse response = httpclient.execute(post)) {
			// String str = IoUtils.inputStreamToString(response.getEntity().getContent());
			// IdResponse idResponse = gson.fromJson(str, IdResponse.class);
			return (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
		}
	}

	private String getBearerToken() {
		if (bearerToken != null) {
			return bearerToken;
		}

		long nowMillis = System.currentTimeMillis();
		Date now = new Date(nowMillis);
		JwtBuilder builder = Jwts.builder()//
				.setIssuedAt(now)
				.setIssuer(jwtIssuer)
				.signWith(applicationKey, SignatureAlgorithm.RS256);
		// optional expiration
		if (JWT_TTL_MILLIS > 0) {
			long expMillis = nowMillis + JWT_TTL_MILLIS;
			Date exp = new Date(expMillis);
			builder.setExpiration(exp);
		}
		bearerToken = builder.compact();
		return bearerToken;
	}

	private String getAccessToken() throws JsonSyntaxException, UnsupportedOperationException, IOException {
		if (accessToken != null) {
			return accessToken;
		}

		int installationId = findInstallationId();

		HttpPost post = new HttpPost("https://api.github.com/app/installations/" + installationId + "/access_tokens");
		post.addHeader("Authorization", "Bearer " + getBearerToken());
		post.addHeader("Accept", "application/vnd.github.v3+json");

		AccessTokenRequest request = new AccessTokenRequest(installationId, new String[] { repository });
		post.setEntity(new StringEntity(gson.toJson(request)));

		try (CloseableHttpResponse response = httpclient.execute(post)) {

			AccessTokensResponse tokens =
					gson.fromJson(new InputStreamReader(response.getEntity().getContent()), AccessTokensResponse.class);
			accessToken = tokens.getToken();
			return accessToken;
		}
	}
}
