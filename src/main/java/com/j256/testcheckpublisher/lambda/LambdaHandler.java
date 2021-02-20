package com.j256.testcheckpublisher.lambda;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpStatus;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.j256.simplelogging.Level;
import com.j256.simplelogging.Logger;
import com.j256.simplelogging.LoggerFactory;
import com.j256.testcheckpublisher.lambda.ApiGatewayRequest.HttpContext;
import com.j256.testcheckpublisher.lambda.ApiGatewayRequest.RequestContext;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunOutput;
import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse;
import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse.ChangedFile;
import com.j256.testcheckpublisher.lambda.github.GithubClient;
import com.j256.testcheckpublisher.lambda.github.GithubClientImpl;
import com.j256.testcheckpublisher.lambda.github.TreeInfoResponse.TreeFile;
import com.j256.testcheckpublisher.plugin.PublishedTestResults;
import com.j256.testcheckpublisher.plugin.TestCheckPubMojo;
import com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResults;

/**
 * Main lambda handler.
 * 
 * @author graywatson
 */
public class LambdaHandler implements RequestStreamHandler {

	private static final String PUBLISHER_PEM_ENV = "github_application_secret";
	private static final String INSTALLTION_ID_SECRET_ENV = "installation_id_secret";
	private static final String DIGEST_ALGORITHM = "SHA1";

	private static final String INSTALLATION_PATH_PREFIX = "/install";
	private static final String FILES_PATH_PREFIX = "/files";
	private static final int FILE_PATH_PREFIX_LENGTH = FILES_PATH_PREFIX.length();
	private static final String RESULTS_PATH_PREFIX = "/results";
	private static final String TEST_PATH_PREFIX = "/test";
	private static final int TEST_PATH_PREFIX_LENGTH = TEST_PATH_PREFIX.length();
	private static final String PROD_PATH_PREFIX = "/prod";
	private static final int PROD_PATH_PREFIX_LENGTH = PROD_PATH_PREFIX.length();

	private static final String FILE_RESOURCE_PREFIX = "files";
	private static final Pattern INSTALLATION_ID_QUERY_PATTERN = Pattern.compile(".*?installation_id=(\\d+).*");
	private static final String INTEGREATION_NAME = "Test Check Publisher";
	private static final String APP_HOME_PAGE = "https://github.com/apps/test-check-publisher";

	// XXX: need an old one too right?
	private static volatile PrivateKey applicationKey;
	private static volatile long installationIdSecret;
	private static final Map<String, String> extToContentType = new HashMap<>();
	private static final Logger logger = LoggerFactory.getLogger(LambdaHandler.class);

	private static CloseableHttpClient httpclient = HttpClients.createDefault();
	private GithubClient testGithub;

	static {
		extToContentType.put("png", "image/png");
		extToContentType.put("jpg", "image/jpeg");
		extToContentType.put("html", "text/html");
		extToContentType.put("txt", "text/plain");
		/*
		 * Try to warm up the various classes by accessing as many classes as we can. Supposedly the CPU given at init
		 * is higher than during runtime so things run faster. Startup used to take ~15 seconds and now take ~3. Big
		 * win. This will try to make a request and fail but that part of it should be cheap.
		 */
		try {
			// turn off logging sicne these will fail
			Logger.setGlobalLogLevel(Level.OFF);
			GithubClient github = GithubClientImpl.createClient(httpclient, getApplicationKey(), "init");
			if (github != null) {
				github.login("init", "init");
			}
		} catch (Throwable th) {
			// ignore it
		} finally {
			Logger.setGlobalLogLevel(null);
		}

	}

	@Override
	public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {

		// read in request of files, sha, repo, secret

		InputStreamReader reader = new InputStreamReader(inputStream);
		Gson gson = new GsonBuilder().create();

		// PublishedTestResults results = new Gson().fromJson(reader, PublishedTestResults.class);
		ApiGatewayRequest request = gson.fromJson(reader, ApiGatewayRequest.class);
		if (request == null) {
			writeResponse(outputStream, gson, HttpStatus.SC_BAD_REQUEST, "text/plain", "Invalid request");
			logger.error("gateway-request is null");
			return;
		}

		String path = request.getRawPath();
		if (path == null || path.length() == 0) {
			writeResponse(outputStream, gson, HttpStatus.SC_NOT_FOUND, "text/plain", "Path not found");
			logRequest(request);
			return;
		}

		// cut off any API prefix
		if (path.startsWith(TEST_PATH_PREFIX)) {
			path = path.substring(TEST_PATH_PREFIX_LENGTH);
		} else if (path.startsWith(PROD_PATH_PREFIX)) {
			path = path.substring(PROD_PATH_PREFIX_LENGTH);
		}

		if (path.startsWith(INSTALLATION_PATH_PREFIX)) {
			handleInstallation(outputStream, gson, request);
		} else if (path.startsWith(FILES_PATH_PREFIX)) {
			handleFile(outputStream, gson, path);
		} else if (path.equals("/") || path.startsWith(RESULTS_PATH_PREFIX)) {
			handleUpload(outputStream, gson, request);
		} else {
			writeResponse(outputStream, gson, HttpStatus.SC_NOT_FOUND, "text/plain", "Path not found: " + path);
		}
		logRequest(request);
	}

	/**
	 * For testing purposes.
	 */
	public static void setApplicationKey(PrivateKey applicationKey) {
		LambdaHandler.applicationKey = applicationKey;
	}

	/**
	 * For testing purposes.
	 */
	public static void setInstallationIdSecret(long installationIdSecret) {
		LambdaHandler.installationIdSecret = installationIdSecret;
	}

	public void setTestGithub(GithubClient testGithub) {
		this.testGithub = testGithub;
	}

	private void logRequest(ApiGatewayRequest request) {
		RequestContext context = request.getRequestContext();
		if (context == null) {
			logger.error("request: request-context is null");
			return;
		}
		HttpContext httpContext = context.getHttpContext();
		if (httpContext == null) {
			logger.error("request: http-context is null");
		} else {
			logger.info("request: " + httpContext.asString());
		}
	}

	private void handleInstallation(OutputStream outputStream, Gson gson, ApiGatewayRequest request)
			throws IOException {

		StringBuilder htmlSb = new StringBuilder();
		htmlSb.append("<html>\n");
		htmlSb.append("<head><title> Test Check Publisher Installation Details </title></head>\n");
		htmlSb.append("<link rel=\"shortcut icon\" href=\"" + FILES_PATH_PREFIX + "/logo.png\" />\n");
		htmlSb.append("<body>\n");
		htmlSb.append("<img src=\"" + FILES_PATH_PREFIX
				+ "/logo.png\" height=35 width=35 alt=\"Test check logo\" style=\"float:left; padding-right:5px;\" /> ");
		htmlSb.append("<h1> Test Check Publisher Installation Information </h1>\n");

		handleInstallBody(request, htmlSb);

		htmlSb.append("<p> For more installation information, please see the ");
		htmlSb.append("<a href=\"" + APP_HOME_PAGE + "\">" + INTEGREATION_NAME + " " + "application page</a>. </p>");
		htmlSb.append("</body>\n");
		htmlSb.append("</html>\n");

		writeResponse(outputStream, gson, HttpStatus.SC_OK, "text/html", htmlSb.toString());
	}

	private void handleInstallBody(ApiGatewayRequest request, StringBuilder htmlSb) {

		// installation_id=1234&setup_action=install
		Matcher matcher = INSTALLATION_ID_QUERY_PATTERN.matcher(request.getRawQueryString());
		if (!matcher.matches()) {
			logger.error("bad installation query string: " + request.getRawQueryString());
			htmlSb.append("<p> Sorry.  The request is not in the format that I expected.  Please try deleting and\n");
			htmlSb.append("    reinstalling the " + INTEGREATION_NAME
					+ " integration to your repository.  Sorry about that. <p>\n");
			return;
		}
		String installationIdStr = matcher.group(1);

		int installationId;
		try {
			installationId = Integer.parseInt(installationIdStr);
		} catch (NumberFormatException nfe) {
			logger.error("installation-id does not parse: " + installationIdStr);
			htmlSb.append(
					"<p> Sorry.  The installation-id is not in the format that I expected.  Please try deleting\n");
			htmlSb.append("    and reinstalling the " + INTEGREATION_NAME
					+ " integration to your repository.  Sorry about that. <p>\n");
			return;
		}

		String secret = createInstallationHash(installationId);
		if (secret == null) {
			// already logged
			htmlSb.append("<p> There is some sort of error in the server configuration.  Sorry about that. <p>\n");
			return;
		}

		PrivateKey applicationKey = getApplicationKey();
		if (applicationKey == null) {
			// already logged
			htmlSb.append("<p> Sorry.  There was a server configuration error.  If the problem\n");
			htmlSb.append("    persists, please submit a github issue. <p>\n");
			return;
		}

		// create our client
		GithubClient github = getGithubClient(APP_HOME_PAGE, applicationKey);
		if (github == null) {
			// already logged
			return;
		}

		String owner;
		try {
			owner = github.findInstallationOwner(installationId);
			logger.info(owner + ": installation hash generated for: " + installationId);
		} catch (IOException ioe) {
			logger.error(ioe, "problems looking up owner for: " + installationId);
			// continue with bogus owner
			owner = "there";
		}

		htmlSb.append("<p> Hi " + owner + ".  Thanks for installing the " + INTEGREATION_NAME
				+ " application.  You will need to add the following environment variable\n");
		htmlSb.append("    to your continuous-integration system. <p>\n");
		htmlSb.append("<p><blockquote><code>")
				.append(TestCheckPubMojo.DEFAULT_SECRET_ENV_NAME)
				.append("=")
				.append(secret)
				.append("</code></blockquote></p>");
	}

	private void handleFile(OutputStream outputStream, Gson gson, String path) throws IOException {

		if (path.length() <= FILE_PATH_PREFIX_LENGTH) {
			return;
		}
		String resourcePath = path.substring(FILE_PATH_PREFIX_LENGTH);
		String filePath = FILE_RESOURCE_PREFIX + resourcePath;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filePath);) {
			if (inputStream == null) {
				writeResponse(outputStream, gson, HttpStatus.SC_NOT_FOUND, "text/plain",
						"File path not found: " + filePath);
				return;
			}
			byte[] buf = new byte[4096];
			while (true) {
				int num = inputStream.read(buf);
				if (num < 0) {
					break;
				}
				baos.write(buf, 0, num);
			}
		}

		Map<String, String> headerMap = Collections.emptyMap();
		int index = resourcePath.lastIndexOf('.');
		if (index > 0) {
			String ext = resourcePath.substring(index + 1);
			String contentType = extToContentType.get(ext);
			if (contentType != null) {
				headerMap = Collections.singletonMap("Content-Type", contentType);
			}
		}

		String base64 = Base64.encodeBase64String(baos.toByteArray());
		writeResponse(outputStream, gson, HttpStatus.SC_OK, headerMap, base64, true);
	}

	private void handleUpload(OutputStream outputStream, Gson gson, ApiGatewayRequest request) throws IOException {

		String body = request.getBody();
		if (body == null || body.length() == 0) {
			// get requests should redirect
			Map<String, String> headerMap = Collections.singletonMap("Location", APP_HOME_PAGE);
			writeResponse(outputStream, gson, HttpStatus.SC_MOVED_PERMANENTLY, headerMap, null, false);
			return;
		}

		// the body is probably base64 encoded because it is json payload
		if (request.isBodyBase64Encoded()) {
			body = new String(Base64.decodeBase64(body));
		}

		PublishedTestResults publishedResults;
		try {
			publishedResults = gson.fromJson(body, PublishedTestResults.class);
		} catch (JsonParseException jpe) {
			logger.error(jpe, "json parse error on published test results");
			writeResponse(outputStream, gson, HttpStatus.SC_BAD_REQUEST, "text/plain",
					"Invalid JSON posted to the server: " + jpe.getMessage());
			return;
		}
		if (publishedResults == null) {
			// request sanity check failed
			logger.error("got null results");
			writeResponse(outputStream, gson, HttpStatus.SC_BAD_REQUEST, "text/plain",
					"Expecting published test results");
			return;
		}

		if (!publishedResults.isMagicCorrect()) {
			// request sanity check failed
			logger.error("request sanity check failed: " + publishedResults.getMagic());
			writeResponse(outputStream, gson, HttpStatus.SC_BAD_REQUEST, "text/plain", "Posted request is invalid");
			return;
		}
		String repository = publishedResults.getRepository();

		FrameworkTestResults frameworkResults = publishedResults.getResults();
		if (StringUtils.isBlank(frameworkResults.getName())) {
			writeResponse(outputStream, gson, HttpStatus.SC_BAD_REQUEST, "text/plain", "No framework name supplied.");
			return;
		}

		String label = publishedResults.getOwner() + "/" + repository;
		logger.info(label + ": uploading @" + publishedResults.getCommitSha());

		PrivateKey applicationKey = getApplicationKey();
		if (applicationKey == null) {
			// already logged
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
					"Server configuration failure");
			return;
		}

		GithubClient github = getGithubClient(label, applicationKey);
		if (github == null) {
			// already logged
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
					"Github client create failure");
			return;
		}

		// login which creates our access-token
		if (!github.login(publishedResults.getOwner(), repository)) {
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
					"Could not login to github for some reason: " + github.getLastStatusLine());
			return;
		}

		// lookup our installation-id and verify our secret
		int installationId = github.getInstallationId();
		if (installationId < 0) {
			logger.error(label + ": no installation-id");
			writeResponse(outputStream, gson, HttpStatus.SC_FORBIDDEN, "text/plain",
					"Could not find installation for application in repository " + repository
							+ ".  You should reinstall the " + INTEGREATION_NAME + " integration.");
			return;
		}

		if (!validateSecret(publishedResults.getSecret(), installationId)) {
			logger.error(label + ": secret did not validate");
			writeResponse(outputStream, gson, HttpStatus.SC_FORBIDDEN, "text/plain",
					"The secret environmental variable value did not validate.  Check your CI envrionment settings.\n"
							+ "You may need to reinstall " + "the " + INTEGREATION_NAME
							+ " integration or check your secret envrionment variable.");
			return;
		}

		// get detail about the commit
		CommitInfoResponse commitInfo = github.requestCommitInfo(publishedResults.getCommitSha());
		if (commitInfo == null) {
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
					"Could not lookup commit information on github for sha " + publishedResults.getCommitSha() + ": "
							+ github.getLastStatusLine());
			return;
		}

		// list all of the files at the commit point
		Collection<TreeFile> treeFiles = github.requestTreeFiles(commitInfo.getTreeSha());
		if (treeFiles == null) {
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
					"Could not get file information for tree sha " + commitInfo.getTreeSha() + ": "
							+ github.getLastStatusLine());
			return;
		}

		Set<String> commitPathSet = new HashSet<>();
		if (commitInfo.getFiles() != null) {
			for (ChangedFile file : commitInfo.getFiles()) {
				// we ignore "removed" files
				if (!"removed".equals(file.getStatus())) {
					commitPathSet.add(file.getFilename());
				}
			}
		}

		// create the check-run request
		CheckRunOutput output = OutputCreatorUtil.createOutput(publishedResults, treeFiles, commitPathSet, label);
		CheckRunRequest checkRunRequest =
				new CheckRunRequest(frameworkResults.getName(), publishedResults.getCommitSha(), output);

		// post the check-run-request
		if (!github.addCheckRun(checkRunRequest)) {
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
					"Could not post check-runs to github: " + github.getLastStatusLine());
			return;
		}

		logger.info(label + ": posted check-run " + output.getTitle());
		writeResponse(outputStream, gson, HttpStatus.SC_OK, "text/plain", "Check-run posted to github.");
	}

	private GithubClient getGithubClient(String label, PrivateKey applicationKey) {
		if (testGithub != null) {
			// for testing purposes
			return testGithub;
		} else {
			return GithubClientImpl.createClient(httpclient, applicationKey, label);
		}
	}

	private void writeResponse(OutputStream outputStream, Gson gson, int statusCode, String contentType, String message)
			throws IOException {
		Map<String, String> headerMap = Collections.singletonMap("Content-Type", contentType);
		writeResponse(outputStream, gson, statusCode, headerMap, message, false);
	}

	private void writeResponse(OutputStream outputStream, Gson gson, int statusCode, Map<String, String> headerMap,
			String message, boolean isBodyBase64Encoded) throws IOException {
		ApiGatewayResponse response = new ApiGatewayResponse(statusCode, null, headerMap, message, isBodyBase64Encoded);
		try (Writer writer = new OutputStreamWriter(outputStream);) {
			gson.toJson(response, writer);
		}
	}

	private boolean validateSecret(String requestSecret, int installationId) {
		String digest = createInstallationHash(installationId);
		return (digest != null && digest.equals(requestSecret));
	}

	String createInstallationHash(int installationId) {
		long secret = getInstallationIdSecret();
		if (secret <= 0) {
			// already logged
			return null;
		}
		long value = installationId ^ secret;

		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
		} catch (NoSuchAlgorithmException nsae) {
			logger.error(nsae, "could not get " + DIGEST_ALGORITHM + " instance");
			return null;
		}

		digest.reset();
		digest.update(Long.toString(value).getBytes());
		return bytesToHex(digest.digest());
	}

	private static PrivateKey getApplicationKey() {
		PrivateKey key = applicationKey;
		if (key != null) {
			return key;
		}

		synchronized (LambdaHandler.class) {
			key = applicationKey;
			if (key != null) {
				return key;
			}
			key = loadKey();
			if (key != null) {
				applicationKey = key;
			}
			return key;
		}
	}

	private long getInstallationIdSecret() {
		long secret = installationIdSecret;
		if (secret != 0) {
			return secret;
		}
		String secretStr = System.getenv(INSTALLTION_ID_SECRET_ENV);
		if (secretStr == null) {
			logger.error("secret env value is null: " + INSTALLTION_ID_SECRET_ENV);
			return -1;
		}
		try {
			secret = Long.parseLong(secretStr);
		} catch (NumberFormatException nfe) {
			// we do't chain the exception to not expose the secret string in the logs
			logger.error("could not parse secret from: " + INSTALLTION_ID_SECRET_ENV);
			return -1;
		}
		installationIdSecret = secret;
		return secret;
	}

	private static PrivateKey loadKey() {
		String pem = System.getenv(PUBLISHER_PEM_ENV);
		if (pem == null) {
			logger.error("publisher secret is null");
			return null;
		}
		try {
			return KeyHandling.readKey(pem);
		} catch (Exception gse) {
			logger.error(gse, "problems loading key from env");
			return null;
		}
	}

	private static String bytesToHex(byte[] digest) {
		return new String(Hex.encodeHex(digest));
	}
}
