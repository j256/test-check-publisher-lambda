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
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunAnnotation;
import com.j256.testcheckpublisher.lambda.github.TreeInfoResponse.TreeFile;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * Github client implementation which handles the access and bearer token.
 * 
 * @author graywatson
 */
public class GithubClientImpl implements GithubClient {

	private static final String GITHUB_APP_ID_ENV = "github_app_id";
	private static final long JWT_TTL_MILLIS = 10 * 60 * 1000;
	private static final String TREE_TYPE = "tree";
	private static final Header ACCEPT_HEADER = new BasicHeader("Accept", "application/vnd.github.v3+json");
	private static final int MAX_CHECK_ANNOTATIONS_PER_REQUEST = 50;
	private static String githubAppId;

	private final CloseableHttpClient httpclient;
	private final String owner;
	private final String repository;
	private final PrivateKey applicationKey;
	private final LambdaLogger logger;
	private final String label;

	private final Gson gson = new Gson();
	private int installationId;
	private Header bearerTokenHeader;
	private Header accessTokenHeader;
	private StatusLine lastStatusLine;

	static {
		githubAppId = System.getenv(GITHUB_APP_ID_ENV);
	}

	private GithubClientImpl(CloseableHttpClient httpclient, String owner, String repository, PrivateKey applicationKey,
			LambdaLogger logger, String label) {
		this.httpclient = httpclient;
		this.owner = owner;
		this.repository = repository;
		this.applicationKey = applicationKey;
		this.logger = logger;
		this.label = label;
	}

	public static GithubClientImpl createClient(CloseableHttpClient httpclient, String owner, String repository,
			PrivateKey applicationKey, LambdaLogger logger, String label) {
		if (githubAppId == null) {
			logger.log("ERROR: Could not find github-app-id env variable\n");
			return null;
		} else {
			return new GithubClientImpl(httpclient, owner, repository, applicationKey, logger, label);
		}
	}

	@Override
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
				logger.log(label + ": ERROR: installation request failed: " + response.getStatusLine() + "\n");
				return -1;
			}

			IdResponse idResponse;
			try {
				idResponse = gson.fromJson(new InputStreamReader(response.getEntity().getContent()), IdResponse.class);
			} catch (JsonParseException jpe) {
				logger.log(label + ": ERROR: installation response json parse failed\n");
				return -1;
			}
			int installationId = idResponse.getId();
			if (installationId <= 0) {
				logger.log(label + ": ERROR: installation request json returned: " + installationId + "\n");
				return -1;
			} else {
				this.installationId = installationId;
				return installationId;
			}
		}
	}

	@Override
	public boolean login() throws IOException {
		return (getAccessTokenHeader() != null);
	}

	@Override
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
				logger.log(label + ": ERROR: commit-info request failed: " + response.getStatusLine() + "\n");
				return null;
			}
		}
	}

	@Override
	public Collection<TreeFile> requestTreeFiles(String sha) throws IOException {

		// GET /repos/{owner}/{repo}/git/trees/{tree_sha}
		HttpGet get = new HttpGet(
				"https://api.github.com/repos/" + owner + "/" + repository + "/git/trees/" + sha + "?recursive=1");
		get.addHeader(getAccessTokenHeader());
		get.addHeader(ACCEPT_HEADER);

		List<TreeFile> fileInfos = new ArrayList<>();
		try (CloseableHttpResponse response = httpclient.execute(get);
				Reader reader = new InputStreamReader(response.getEntity().getContent());) {

			lastStatusLine = response.getStatusLine();
			if (lastStatusLine.getStatusCode() != HttpStatus.SC_OK) {
				logger.log(repository + ": ERROR: tree request failed: " + response.getStatusLine() + "\n");
				return null;
			}

			TreeInfoResponse treeInfoResponse = gson.fromJson(reader, TreeInfoResponse.class);
			if (treeInfoResponse.getTreeFiles() != null) {
				for (TreeFile treeFile : treeInfoResponse.getTreeFiles()) {
					if (!TREE_TYPE.equals(treeFile.getType())) {
						// make our path a relative path from root
						fileInfos.add(treeFile);
					}
				}
			}
		}
		return fileInfos;
	}

	@Override
	public boolean addCheckRun(CheckRunRequest request) throws IOException {

		// make sure the number of annotations is below the per request limit
		List<CheckRunAnnotation> annotations = request.output.annotations;
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

	@Override
	public StatusLine getLastStatusLine() {
		return lastStatusLine;
	}

	/**
	 * For testing purposes.
	 */
	public static void setGithubAppId(String githubAppId) {
		GithubClientImpl.githubAppId = githubAppId;
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
				logger.log(label + ": ERROR: check-runs request failed: " + response.getStatusLine() + "\n");
				logger.log(label + ": ERROR: results: " + responseToString(response) + "\n");
				return false;
			}
		}
	}

	private String responseToString(CloseableHttpResponse response) throws IOException {
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

		AccessTokenRequest request = new AccessTokenRequest(installationId, repository);
		post.setEntity(new StringEntity(gson.toJson(request)));

		try (CloseableHttpResponse response = httpclient.execute(post)) {
			lastStatusLine = response.getStatusLine();
			if (lastStatusLine.getStatusCode() != HttpStatus.SC_CREATED) {
				return null;
			}
			AccessTokenResponse tokens =
					gson.fromJson(new InputStreamReader(response.getEntity().getContent()), AccessTokenResponse.class);
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
				.setIssuer(githubAppId)
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
