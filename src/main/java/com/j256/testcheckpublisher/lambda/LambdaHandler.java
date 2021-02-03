package com.j256.testcheckpublisher.lambda;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.security.GeneralSecurityException;
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
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.j256.testcheckpublisher.lambda.ApiGatewayRequest.HttpContext;
import com.j256.testcheckpublisher.lambda.ApiGatewayRequest.RequestContext;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckLevel;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunAnnotation;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunOutput;
import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse;
import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse.ChangedFile;
import com.j256.testcheckpublisher.lambda.github.GithubClient;
import com.j256.testcheckpublisher.lambda.github.GithubClientImpl;
import com.j256.testcheckpublisher.lambda.github.TreeInfoResponse.TreeFile;
import com.j256.testcheckpublisher.plugin.PublishedTestResults;
import com.j256.testcheckpublisher.plugin.TestCheckPubMojo;
import com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResults;
import com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResults.TestFileResult;
import com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResults.TestFileResult.TestLevel;

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
	private static final String FILES_PATH_PREFIX = "/files/";
	private static final int FILE_PATH_PREFIX_LENGTH = FILES_PATH_PREFIX.length();
	private static final String RESULTS_PATH_PREFIX = "/results";
	private static final String TEST_PATH_PREFIX = "/test";
	private static final int TEST_PATH_PREFIX_LENGTH = TEST_PATH_PREFIX.length();
	private static final String PROD_PATH_PREFIX = "/prod";
	private static final int PROD_PATH_PREFIX_LENGTH = PROD_PATH_PREFIX.length();

	private static final String FILE_RESOURCE_PREFIX = "files/";
	private static final Pattern INSTALLATION_ID_QUERY_PATTERN = Pattern.compile(".*?installation_id=(\\d+).*");
	private static final String INTEGREATION_NAME = "Test Check Publisher";

	// XXX: need an old one too right?
	private static volatile PrivateKey applicationKey;
	private static volatile long installationIdSecret;
	private static final Map<String, String> extToContentType = new HashMap<>();

	private GithubClient testGithub;

	static {
		extToContentType.put("png", "image/png");
		extToContentType.put("jpg", "image/jpeg");
		extToContentType.put("html", "text/html");
		extToContentType.put("txt", "text/plain");
	}

	@Override
	public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
		LambdaLogger logger = context.getLogger();

		// read in request of files, sha, repo, secret

		InputStreamReader reader = new InputStreamReader(inputStream);
		Gson gson = new GsonBuilder().create();

		// PublishedTestResults results = new Gson().fromJson(reader, PublishedTestResults.class);
		ApiGatewayRequest request = gson.fromJson(reader, ApiGatewayRequest.class);
		if (request == null) {
			writeResponse(outputStream, gson, HttpStatus.SC_BAD_REQUEST, "text/plain", "Invalid request");
			logger.log("gateway-request is null\n");
			return;
		}

		String rawPath = request.getRawPath();
		if (rawPath == null || rawPath.length() == 0) {
			writeResponse(outputStream, gson, HttpStatus.SC_NOT_FOUND, "text/plain", "Path not found");
			logRequest(logger, request);
			return;
		}

		// cut off any API prefix
		if (rawPath.startsWith(TEST_PATH_PREFIX)) {
			rawPath = rawPath.substring(TEST_PATH_PREFIX_LENGTH);
		} else if (rawPath.startsWith(PROD_PATH_PREFIX)) {
			rawPath = rawPath.substring(PROD_PATH_PREFIX_LENGTH);
		}

		if (rawPath.startsWith(INSTALLATION_PATH_PREFIX)) {
			handleInstallation(outputStream, logger, gson, request);
		} else if (rawPath.startsWith(FILES_PATH_PREFIX)) {
			handleFile(outputStream, logger, gson, rawPath);
		} else if (rawPath.equals("/") || rawPath.startsWith(RESULTS_PATH_PREFIX)) {
			handleUploadTests(outputStream, logger, gson, request);
		} else {
			writeResponse(outputStream, gson, HttpStatus.SC_NOT_FOUND, "text/plain", "Path not found: " + rawPath);
		}
		logRequest(logger, request);
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

	private void logRequest(LambdaLogger logger, ApiGatewayRequest request) {
		RequestContext context = request.getContext();
		if (context == null) {
			logger.log("request: request-context is null\n");
			return;
		}
		HttpContext httpContext = context.getHttpContext();
		if (httpContext == null) {
			logger.log("request: http-context is null\n");
		} else {
			logger.log("request: " + httpContext.asString() + "\n");
		}
	}

	private void handleInstallation(OutputStream outputStream, LambdaLogger logger, Gson gson,
			ApiGatewayRequest request) throws IOException {

		StringBuilder html = new StringBuilder();
		html.append("<html>\n");
		html.append("<head><title> Test Check Publisher Installation Details </title></head>\n");
		html.append("<link rel=\"shortcut icon\" href=\"/files/logo.png\" />\n");
		html.append("<body>\n");
		html.append(
				"<img src=\"/files/logo.png\" height=50 width=50 alt=\"logo\" style=\"float:left; padding-right:5px;\" /> ");
		html.append("<h1> Test Check Publisher Installation Information </h1>\n");

		handleInstallBody(logger, request, html);

		html.append("</body>\n");
		html.append("</html>\n");

		writeResponse(outputStream, gson, HttpStatus.SC_OK, "text/html", html.toString());
	}

	private void handleInstallBody(LambdaLogger logger, ApiGatewayRequest request, StringBuilder html) {
		// installation_id=1234&setup_action=install
		Matcher matcher = INSTALLATION_ID_QUERY_PATTERN.matcher(request.getRawQueryString());
		if (!matcher.matches()) {
			logger.log("installation query string in strange format: " + request.getRawQueryString() + "\n");
			html.append("<p> Sorry.  The request is not in the format that I expected.  Please try deleting and\n");
			html.append("    reinstalling the " + INTEGREATION_NAME
					+ " integration to your repository.  Sorry about that. <p>\n");
			return;
		}
		String installationIdStr = matcher.group(1);

		int installationId;
		try {
			installationId = Integer.parseInt(installationIdStr);
		} catch (NumberFormatException nfe) {
			logger.log("installation-id does not parse: " + installationIdStr + "\n");
			html.append("<p> Sorry.  The installation-id is not in the format that I expected.  Please try deleting\n");
			html.append("    and reinstalling the " + INTEGREATION_NAME
					+ " integration to your repository.  Sorry about that. <p>\n");
			return;
		}
		String secret = createInstallationHash(logger, installationId);
		if (secret == null) {
			logger.log("installation hash generated as null for: " + installationId + "\n");
			html.append("<p> There is some sort of error in the server configuration.  Sorry about that. <p>\n");
			return;
		}

		html.append("<p> Thanks for installing the " + INTEGREATION_NAME
				+ " integration.  You will need to add the following environment variable\n");
		html.append("    to your continuous-integration system. <p>\n");
		html.append("<p><blockquote><code>")
				.append(TestCheckPubMojo.DEFAULT_SECRET_ENV_NAME)
				.append("=")
				.append(secret)
				.append("</code></blockquote></p>");
		html.append("<p> For more information, please see the ");
		html.append("<a href=\"https://github.com/apps/test-check-publisher\">" + INTEGREATION_NAME + " "
				+ "application page</a>. </p>");
	}

	private void handleFile(OutputStream outputStream, LambdaLogger logger, Gson gson, String path) throws IOException {

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

	private void handleUploadTests(OutputStream outputStream, LambdaLogger logger, Gson gson, ApiGatewayRequest request)
			throws IOException {

		String body = request.getBody();
		if (body == null || body.length() == 0) {
			// get requests should redirect
			Map<String, String> headerMap =
					Collections.singletonMap("Location", "https://github.com/apps/test-check-publisher");
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
		} catch (JsonParseException jse) {
			writeResponse(outputStream, gson, HttpStatus.SC_BAD_REQUEST, "text/plain",
					"Expecting published test results");
			return;
		}
		if (publishedResults == null) {
			// request sanity check failed
			logger.log("got null results\n");
			writeResponse(outputStream, gson, HttpStatus.SC_BAD_REQUEST, "text/plain",
					"Expecting published test results");
			return;
		}

		if (!publishedResults.isMagicCorrect()) {
			// request sanity check failed
			logger.log("request sanity check failed: " + publishedResults.getMagic() + "\n");
			writeResponse(outputStream, gson, HttpStatus.SC_BAD_REQUEST, "text/plain", "Posted request is invalid");
			return;
		}
		String repository = publishedResults.getRepository();

		logger.log(publishedResults.getOwner() + "/" + repository + "@" + publishedResults.getCommitSha() + "\n");

		PrivateKey applicationKey = getApplicationKey(logger);
		if (applicationKey == null) {
			// already logged
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
					"Application key failure");
			return;
		}

		CloseableHttpClient httpclient = HttpClients.createDefault();
		GithubClient github = testGithub;
		if (github == null) {
			github = GithubClientImpl.createClient(httpclient, publishedResults.getOwner(), repository, applicationKey,
					logger);
			if (github == null) {
				// already logged
				writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
						"Github client create failure");
				return;
			}
		}

		// lookup our installation-id and verify our secret
		int installationId = github.findInstallationId();
		if (installationId <= 0) {
			logger.log(repository + ": no installation-id\n");
			writeResponse(outputStream, gson, HttpStatus.SC_FORBIDDEN, "text/plain",
					"Could not find installation for application in repository " + repository
							+ ".  You should reinstall the " + INTEGREATION_NAME + " integration.");
			return;
		}

		if (!validateSecret(logger, publishedResults.getSecret(), installationId)) {
			logger.log(repository + ": secret did not validate\n");
			writeResponse(outputStream, gson, HttpStatus.SC_FORBIDDEN, "text/plain",
					"The secret environmental variable value did not validate.  You may need to reinstall " + "the "
							+ INTEGREATION_NAME + " integration.");
			return;
		}

		// login which creates our access-token
		if (!github.login()) {
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
					"Could not login to github for some reason: " + github.getLastStatusLine());
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

		Set<String> commitPathSet = new HashSet<>();
		if (commitInfo.getFiles() != null) {
			for (ChangedFile file : commitInfo.getFiles()) {
				// we ignore "removed" files
				if (!"removed".equals(file.getStatus())) {
					commitPathSet.add(file.getFilename());
				}
			}
		}

		// list all of the files at the commit point
		Collection<TreeFile> treeFiles = github.requestTreeFiles(commitInfo.getTreeSha());
		if (treeFiles == null) {
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
					"Could not get tree file information for tree sha " + commitInfo.getTreeSha() + ": "
							+ github.getLastStatusLine());
			return;
		}

		FrameworkTestResults frameworkResults = publishedResults.getResults();
		GithubFormat format = GithubFormat.fromString(frameworkResults.getFormat());

		// create the check-run request
		CheckRunOutput output =
				createRequest(logger, publishedResults, treeFiles, commitPathSet, frameworkResults, format);
		CheckRunRequest checkRunRequest =
				new CheckRunRequest(frameworkResults.getName(), publishedResults.getCommitSha(), output);

		// post the check-run-request
		if (!github.addCheckRun(checkRunRequest)) {
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
					"Could not post check-runs to github: " + github.getLastStatusLine());
			return;
		}

		logger.log(repository + ": posted check-run " + output.getTitle() + "\n");
		writeResponse(outputStream, gson, HttpStatus.SC_OK, "text/plain", "Check-run posted to github.");
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

	private CheckRunOutput createRequest(LambdaLogger logger, PublishedTestResults publishedResults,
			Collection<TreeFile> treeFiles, Set<String> commitPathSet, FrameworkTestResults frameworkResults,
			GithubFormat format) {

		String owner = publishedResults.getOwner();
		String repository = publishedResults.getRepository();
		String commitSha = publishedResults.getCommitSha();

		CheckRunOutput output = new CheckRunOutput();

		/*
		 * Create a map of path portions to file names, the idea being that we may have classes not laid out in a nice
		 * hierarchy and we don't want to read all files looking for package ...
		 */
		Map<String, FileInfo> nameMap = new HashMap<>();
		for (TreeFile treeFile : treeFiles) {
			String path = treeFile.getPath();
			FileInfo fileInfo = new FileInfo(path, treeFile.getSha(), commitPathSet.contains(treeFile.getPath()));
			nameMap.put(path, fileInfo);
			nameMap.put(fileInfo.getName(), fileInfo);
			int index = 0;
			while (true) {
				int nextIndex = path.indexOf('/', index);
				if (nextIndex < 0) {
					break;
				}
				index = nextIndex + 1;
				nameMap.put(path.substring(index), fileInfo);
			}
			// should be just the name
			String fileName = path.substring(index);
			nameMap.put(fileName, fileInfo);
			// also cut off the extension
			index = fileName.indexOf('.');
			if (index > 0) {
				fileName = fileName.substring(0, index);
				nameMap.put(fileName, fileInfo);
			}
		}

		StringBuilder textSb = new StringBuilder();

		if (frameworkResults == null) {
			logger.log(repository + ": no framework results\n");
		} else {
			if (frameworkResults.getFileResults() != null) {
				Set<String> badPathSet = new HashSet<>();
				for (TestFileResult fileResult : frameworkResults.getFileResults()) {
					if (badPathSet.contains(fileResult.getPath())) {
						// no reason to generate multiple errors
						continue;
					}
					FileInfo fileInfo = mapFileByPath(nameMap, fileResult.getPath());
					if (fileInfo == null) {
						logger.log(repository + ": could not locate file associated with test path: "
								+ fileResult.getPath() + "\n");
						badPathSet.add(fileResult.getPath());
					} else {
						addTestResult(owner, repository, commitSha, output, fileResult, fileInfo, format, textSb);
					}
				}
			}
			output.addCounts(frameworkResults.getNumTests(), frameworkResults.getNumFailures(),
					frameworkResults.getNumErrors());
		}
		output.sortAnnotations();

		String title = output.getTestCount() + " tests, " + output.getFailureCount() + " failures, "
				+ output.getErrorCount() + " errors";
		output.setTitle(title);
		output.setText(textSb.toString());

		return output;
	}

	private FileInfo mapFileByPath(Map<String, FileInfo> nameMap, String testPath) {

		FileInfo result = nameMap.get(testPath);
		if (result != null) {
			return result;
		}

		int index = 0;
		while (true) {
			int nextIndex = testPath.indexOf('/', index);
			if (nextIndex < 0) {
				break;
			}
			index = nextIndex + 1;
			result = nameMap.get(testPath.substring(index));
			if (result != null) {
				return result;
			}
		}
		// could be just the name
		return nameMap.get(testPath.substring(index));
	}

	private void addTestResult(String owner, String repository, String commitSha, CheckRunOutput output,
			TestFileResult fileResult, FileInfo fileInfo, GithubFormat format, StringBuilder textSb) {

		CheckLevel level = CheckLevel.fromTestLevel(fileResult.getTestLevel());
		output.addAnnotation(
				new CheckRunAnnotation(fileInfo.getPath(), fileResult.getLineNumber(), fileResult.getLineNumber(),
						level, fileResult.getTestName(), fileResult.getMessage(), fileResult.getDetails()));

		/*
		 * If the file is not referenced in the commit then we add into the text of the check a reference to it. The
		 * commit might make a change to a source file and fail a unit test that is not part of the commit. This results
		 * in effectively a broken link in the annotation file reference unfortunately.
		 */
		if (format.isWriteDetails() && !fileInfo.isInCommit() && fileResult.getTestLevel() != TestLevel.NOTICE) {
			// NOTE: html seems to be filtered here
			textSb.append("* ");
			appendEscapedMessage(textSb, fileResult.getMessage());
			textSb.append(' ')
					.append("https://github.com/")
					.append(owner)
					.append('/')
					.append(repository)
					.append("/blob/")
					.append(commitSha)
					.append('/')
					.append(fileInfo.getPath())
					.append("#L")
					.append(fileResult.getLineNumber())
					.append('\n');
		}
	}

	private void appendEscapedMessage(StringBuilder sb, String msg) {
		int len = msg.length();
		for (int i = 0; i < len; i++) {
			char ch = msg.charAt(i);
			if (ch == '<') {
				sb.append("&lt;");
			} else if (ch == '>') {
				sb.append("&gt;");
			} else if (ch == '&') {
				sb.append("&amp;");
			} else {
				sb.append(ch);
			}
		}
	}

	private boolean validateSecret(LambdaLogger logger, String requestSecret, int installationId) {
		String digest = createInstallationHash(logger, installationId);
		return (digest != null && digest.equals(requestSecret));
	}

	String createInstallationHash(LambdaLogger logger, int installationId) {
		long secret = getInstallationIdSecret(logger);
		if (secret <= 0) {
			// already logged
			return null;
		}
		long value = installationId ^ secret;

		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
		} catch (NoSuchAlgorithmException nsae) {
			logger.log("could not get " + DIGEST_ALGORITHM + " instance\n");
			logger.log(throwableToString(nsae));
			return null;
		}

		digest.reset();
		digest.update(Long.toString(value).getBytes());
		return bytesToHex(digest.digest());
	}

	private PrivateKey getApplicationKey(LambdaLogger logger) throws IOException {
		PrivateKey key = applicationKey;
		if (key != null) {
			return key;
		}

		synchronized (LambdaHandler.class) {
			key = applicationKey;
			if (key != null) {
				return key;
			}
			key = loadKey(logger);
			if (key != null) {
				applicationKey = key;
			}
			return key;
		}
	}

	private long getInstallationIdSecret(LambdaLogger logger) {
		long secret = installationIdSecret;
		if (secret != 0) {
			return secret;
		}
		String secretStr = System.getenv(INSTALLTION_ID_SECRET_ENV);
		if (secretStr == null) {
			logger.log("secret env value is null: " + INSTALLTION_ID_SECRET_ENV + "\n");
			return -1;
		}
		try {
			secret = Long.parseLong(secretStr);
		} catch (NumberFormatException nfe) {
			// we do't chain the exception to not expose the secret string in the logs
			logger.log("Could not parse long secret string from: " + INSTALLTION_ID_SECRET_ENV + "\n");
			return -1;
		}
		installationIdSecret = secret;
		return secret;
	}

	private PrivateKey loadKey(LambdaLogger logger) throws IOException {
		String pem = System.getenv(PUBLISHER_PEM_ENV);
		if (pem == null) {
			logger.log("publisher secret env is null\n");
			return null;
		}
		try {
			return KeyHandling.readKey(pem);
		} catch (GeneralSecurityException gse) {
			logger.log("problems loading application-key from pem env\n");
			logger.log(throwableToString(gse));
			return null;
		}
	}

	private static String throwableToString(Throwable throwable) {
		if (throwable == null) {
			return null;
		}

		StringWriter writer = new StringWriter();
		try (PrintWriter printWriter = new PrintWriter(writer);) {
			throwable.printStackTrace(printWriter);
		}
		return writer.toString();
	}

	private static String bytesToHex(byte[] digest) {
		return new String(Hex.encodeHex(digest));
	}

	/**
	 * Format of the results that we post to github.
	 */
	private static enum GithubFormat {
		DEFAULT,
		// end
		;

		/**
		 * Write failures and errors into the details if they aren't in the commit.
		 */
		public boolean isWriteDetails() {
			return true;
		}

		/**
		 * Find the format for our string returning {@link #DEFAULT} if not found.
		 */
		public static GithubFormat fromString(String str) {
			if (str == null) {
				return DEFAULT;
			}
			for (GithubFormat format : values()) {
				if (format.name().equalsIgnoreCase(str)) {
					return format;
				}
			}
			return DEFAULT;
		}
	}
}
