package com.j256.testcheckpublisher.lambda.github;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.j256.testcheckpublisher.lambda.FileInfo;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunAnnotation;
import com.j256.testcheckpublisher.lambda.github.TreeInfoResponse.TreeFile;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * Github client wrapper which handles the access and bearer token.
 * 
 * @author graywatson
 */
public class GithubClient {

	private static final String GITHUB_APP_ID_ENV = "github_app_id";
	private static final long JWT_TTL_MILLIS = 10 * 60 * 1000;
	private static final String TREE_TYPE = "tree";
	private static final Header ACCEPT_HEADER = new BasicHeader("Accept", "application/vnd.github.v3+json");
	private static final int MAX_CHECK_ANNOTATIONS_PER_REQUEST = 50;

	private final CloseableHttpClient httpclient;
	private final String owner;
	private final String repository;
	private final PrivateKey applicationKey;
	private final String jwtIssuer;
	private final LambdaLogger logger;

	private final Gson gson = new Gson();
	private int installationId;
	private Header bearerTokenHeader;
	private Header accessTokenHeader;
	private StatusLine lastStatusLine;

	public GithubClient(CloseableHttpClient httpclient, String owner, String repository, PrivateKey applicationKey,
			LambdaLogger logger) {
		this.httpclient = httpclient;
		this.owner = owner;
		this.repository = repository;
		this.applicationKey = applicationKey;
		this.logger = logger;

		this.jwtIssuer = System.getenv(GITHUB_APP_ID_ENV);
		if (jwtIssuer == null) {
			throw new IllegalStateException("Could not find JWT issuer env variable");
		}
	}

	/**
	 * Find and return the installation-id for a particular repo.
	 */
	public int findInstallationId() throws IOException {

		if (installationId != 0) {
			return installationId;
		}

		HttpGet get = new HttpGet("https://api.github.com/repos/" + owner + "/" + repository + "/installation");
		get.addHeader(getBearerTokenHeader());
		get.addHeader(ACCEPT_HEADER);
		try (CloseableHttpResponse response = httpclient.execute(get)) {

			lastStatusLine = response.getStatusLine();
			if (lastStatusLine.getStatusCode() != HttpStatus.SC_OK) {
				logger.log(repository + ": installation request failed: " + response.getStatusLine() + "\n");
				return 0;
			}

			IdResponse idResponse =
					gson.fromJson(new InputStreamReader(response.getEntity().getContent()), IdResponse.class);
			return idResponse.getId();
		}
	}

	/**
	 * Login to github meaning get the access token.
	 */
	public boolean login() throws IOException {
		return (getAccessTokenHeader() != null);
	}

	/**
	 * Get information about a commit.
	 */
	public CommitInfoResponse requestCommitInfo(String topSha)
			throws JsonSyntaxException, UnsupportedOperationException, IOException {

		HttpGet get = new HttpGet("https://api.github.com/repos/" + owner + "/" + repository + "/commits/" + topSha);
		get.addHeader(ACCEPT_HEADER);

		try (CloseableHttpResponse response = httpclient.execute(get);
				Reader contentReader = new InputStreamReader(response.getEntity().getContent());) {
			lastStatusLine = response.getStatusLine();
			if (lastStatusLine.getStatusCode() == HttpStatus.SC_OK) {
				return gson.fromJson(contentReader, CommitInfoResponse.class);
			} else {
				logger.log(repository + ": commit-info request failed: " + response.getStatusLine() + "\n");
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
		get.addHeader(getAccessTokenHeader());
		get.addHeader(ACCEPT_HEADER);

		List<FileInfo> fileInfos = new ArrayList<>();
		try (CloseableHttpResponse response = httpclient.execute(get);
				Reader reader = new InputStreamReader(response.getEntity().getContent());) {

			lastStatusLine = response.getStatusLine();
			if (lastStatusLine.getStatusCode() != HttpStatus.SC_OK) {
				logger.log(repository + ": tree request failed: " + response.getStatusLine() + "\n");
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

		// make sure the number of annotations is below the per request limit
		Collection<CheckRunAnnotation> annotations = request.output.annotations;
		if (annotations == null || annotations.size() < MAX_CHECK_ANNOTATIONS_PER_REQUEST) {
			return doCheckRunPost(request);
		}

		// turn into a list so we can twiddle with it
		annotations = new ArrayList<>(annotations);
		Iterator<CheckRunAnnotation> iterator = annotations.iterator();

		List<CheckRunAnnotation> requestAnnotations = new ArrayList<>(MAX_CHECK_ANNOTATIONS_PER_REQUEST);
		while (annotations.size() > MAX_CHECK_ANNOTATIONS_PER_REQUEST) {
			requestAnnotations.clear();
			// add the annotations from main list to the per-request list
			for (int i = 0; i < MAX_CHECK_ANNOTATIONS_PER_REQUEST; i++) {
				// add to the request list
				requestAnnotations.add(iterator.next());
				// remove from the main list
				iterator.remove();
			}
			request.output.annotations = requestAnnotations;
			if (!doCheckRunPost(request)) {
				return false;
			}
			// loop around and send the next batch
		}
		request.output.annotations = annotations;
		return doCheckRunPost(request);
	}

	private boolean doCheckRunPost(CheckRunRequest request)
			throws IOException, UnsupportedEncodingException, ClientProtocolException {
		HttpPost post = new HttpPost("https://api.github.com/repos/" + owner + "/" + repository + "/check-runs");
		post.addHeader(getAccessTokenHeader());
		post.addHeader(ACCEPT_HEADER);

		post.setEntity(new StringEntity(gson.toJson(request)));

		try (CloseableHttpResponse response = httpclient.execute(post)) {
			lastStatusLine = response.getStatusLine();
			if (lastStatusLine.getStatusCode() == HttpStatus.SC_CREATED) {
				return true;
			} else {
				logger.log(repository + ": check-runs request failed: " + response.getStatusLine() + "\n");
				logger.log("results: " + responseToString(response));
				return false;
			}
		}
	}

	public StatusLine getLastStatusLine() {
		return lastStatusLine;
	}

	public String responseToString(CloseableHttpResponse response) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
				StringWriter writer = new StringWriter()) {
			char[] buf = new char[1024];
			while (true) {
				int num = reader.read(buf);
				if (num < 0) {
					return writer.toString();
				}
				writer.write(buf, 0, num);
			}
		}
	}

	private Header getAccessTokenHeader() throws JsonSyntaxException, UnsupportedOperationException, IOException {
		if (accessTokenHeader != null) {
			return accessTokenHeader;
		}

		int installationId = findInstallationId();

		HttpPost post = new HttpPost("https://api.github.com/app/installations/" + installationId + "/access_tokens");
		post.addHeader(getBearerTokenHeader());
		post.addHeader(ACCEPT_HEADER);

		AccessTokenRequest request = new AccessTokenRequest(installationId, new String[] { repository });
		post.setEntity(new StringEntity(gson.toJson(request)));

		try (CloseableHttpResponse response = httpclient.execute(post)) {
			lastStatusLine = response.getStatusLine();
			if (lastStatusLine.getStatusCode() != HttpStatus.SC_CREATED) {
				return null;
			}
			AccessTokensResponse tokens =
					gson.fromJson(new InputStreamReader(response.getEntity().getContent()), AccessTokensResponse.class);
			String accessToken = tokens.getToken();
			if (accessToken == null || accessToken.length() == 0) {
				return null;
			}
			accessTokenHeader = new BasicHeader("Authorization", "token " + accessToken);
			return accessTokenHeader;
		}
	}

	private Header getBearerTokenHeader() {
		if (bearerTokenHeader != null) {
			return bearerTokenHeader;
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
		String bearerToken = builder.compact();
		bearerTokenHeader = new BasicHeader("Authorization", "Bearer " + bearerToken);
		return bearerTokenHeader;
	}
}
