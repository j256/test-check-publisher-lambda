package com.j256.testcheckpublisher.lambda;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckLevel;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunAnnotation;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunOutput;
import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse;
import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse.ChangedFile;
import com.j256.testcheckpublisher.lambda.github.GithubClient;
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
	private static final String SHA1_ALGORITHM = "SHA1";
	private static final String INSTALLATION_PATH_PREFIX = "/install";
	private static final String FILE_PATH_PREFIX = "/files/";
	private static final int FILE_PATH_PREFIX_LENGTH = FILE_PATH_PREFIX.length();
	private static final String FILE_RESOURCE_PREFIX = "files/";
	private static final Pattern INSTALLATION_ID_QUERY_PATTERN = Pattern.compile(".*?installation_id=(\\d+).*");
	private static final String INTEGREATION_NAME = "Test Check Publisher";

	// XXX: need an old one too right?
	private static volatile PrivateKey applicationKey;
	private static volatile long installationIdSecret;
	private static final Map<String, String> extToContentType = new HashMap<>();

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

		String rawPath = request.getRawPath();
		if (rawPath != null && rawPath.startsWith(INSTALLATION_PATH_PREFIX)) {
			handleInstallation(outputStream, logger, gson, request);
		} else if (rawPath != null && rawPath.startsWith(FILE_PATH_PREFIX)) {
			handleFile(outputStream, logger, gson, rawPath);
		} else {
			handleUploadTests(outputStream, logger, gson, request);
		}
	}

	private void handleInstallation(OutputStream outputStream, LambdaLogger logger, Gson gson,
			ApiGatewayRequest request) throws IOException {

		StringBuilder html = new StringBuilder();
		html.append("<html>\n");
		html.append("<head><title> Test Check Publisher Installation Information </title></head>\n");
		html.append("<body>\n");
		html.append("<img src=\"/files/logo.png\" height=50 width=50 alt=\"logo\" style=\"float:left;\" /> ");
		html.append("<h1> Test Check Publisher Installation Information </h1>\n");

		// installation_id=1234&setup_action=install
		Matcher matcher = INSTALLATION_ID_QUERY_PATTERN.matcher(request.getRawQueryString());
		if (matcher.matches()) {
			int installationId = Integer.parseInt(matcher.group(1));
			html.append("<p> Thanks for installing the " + INTEGREATION_NAME
					+ " integration.  You will need to add the following environment variable\n");
			html.append("    info your continuous-integration system.  Please save this: <p>\n");
			String secret = createInstallationHash(installationId);
			html.append("<p><blockquote><code>")
					.append(TestCheckPubMojo.DEFAULT_SECRET_ENV_NAME)
					.append("=")
					.append(secret)
					.append("</code></blockquote></p>");
			html.append("<p> For more information, please see the ");
			html.append("<a href=\"https://github.com/apps/test-check-publisher\">" + INTEGREATION_NAME + " "
					+ "application page</a>. </p>");
		} else {
			logger.log("installation query string in strange format: " + request.getRawQueryString() + "\n");
			html.append("<p> Sorry.  The request is not in the format that I expected.  Please try deleting and\n");
			html.append("    reinstalling the " + INTEGREATION_NAME
					+ " integration to your repository.  Sorry about that. <p>\n");
		}

		html.append("</body>\n");
		html.append("</html>\n");

		writeResponse(outputStream, gson, HttpStatus.SC_OK, "text/html", html.toString());
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
		ApiGatewayResponse response = new ApiGatewayResponse(HttpStatus.SC_OK, null, headerMap, base64, true);
		try (Writer writer = new OutputStreamWriter(outputStream);) {
			gson.toJson(response, writer);
		}
	}

	private void handleUploadTests(OutputStream outputStream, LambdaLogger logger, Gson gson, ApiGatewayRequest request)
			throws IOException {

		String body = request.getBody();

		// the body is probably base64 encoded because it is json payload
		if (request.isBodyBase64Encoded()) {
			body = new String(Base64.decodeBase64(body));
		}

		PublishedTestResults publishedResults = gson.fromJson(body, PublishedTestResults.class);
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

		CloseableHttpClient httpclient = HttpClients.createDefault();

		GithubClient github =
				new GithubClient(httpclient, publishedResults.getOwner(), repository, getApplicationKey(), logger);

		// lookup our installation-id and verify our secret
		int installationId = github.findInstallationId();
		if (installationId <= 0) {
			logger.log(repository + ": no installation-id\n");
			writeResponse(outputStream, gson, HttpStatus.SC_NOT_FOUND, "text/plain",
					"Could not find installation for application in repository " + repository
							+ ".  You should reinstall the " + INTEGREATION_NAME + " integration.");
			return;
		}

		if (!validateSecret(publishedResults.getSecret(), installationId)) {
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
		Collection<FileInfo> fileInfos = github.requestTreeFiles(commitInfo.getTreeSha());
		if (fileInfos == null) {
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
					"Could not get tree file information for tree sha " + commitInfo.getTreeSha() + ": "
							+ github.getLastStatusLine());
			return;
		}
		for (FileInfo fileInfo : fileInfos) {
			if (commitPathSet.contains(fileInfo.getPath())) {
				fileInfo.setInCommit(true);
			}
		}

		FrameworkTestResults frameworkResults = publishedResults.getResults();
		GithubFormat format = GithubFormat.fromString(frameworkResults.getFormat());

		// create the check-run request
		CheckRunOutput output = createRequest(logger, publishedResults, fileInfos, frameworkResults, format);
		CheckRunRequest checkRunRequest =
				new CheckRunRequest(frameworkResults.getName(), publishedResults.getCommitSha(), output);

		// post the check-run-request
		if (!github.addCheckRun(checkRunRequest)) {
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR, "text/plain",
					"Could not post check-runs to github: " + github.getLastStatusLine());
			return;
		}

		logger.log(repository + ": posted check-run " + output.getTitle() + "\n");
		writeResponse(outputStream, gson, HttpStatus.SC_OK, "text/plain",
				"Check-run posted to github: " + github.getLastStatusLine());
	}

	private void writeResponse(OutputStream outputStream, Gson gson, int statusCode, String contentType, String message)
			throws IOException {
		Map<String, String> headerMap = Collections.singletonMap("Content-Type", contentType);
		ApiGatewayResponse response = new ApiGatewayResponse(statusCode, null, headerMap, message, false);
		try (Writer writer = new OutputStreamWriter(outputStream);) {
			gson.toJson(response, writer);
		}
	}

	private CheckRunOutput createRequest(LambdaLogger logger, PublishedTestResults publishedResults,
			Collection<FileInfo> fileInfos, FrameworkTestResults frameworkResults, GithubFormat format) {

		String owner = publishedResults.getOwner();
		String repository = publishedResults.getRepository();
		String commitSha = publishedResults.getCommitSha();

		CheckRunOutput output = new CheckRunOutput();

		/*
		 * Create a map of path portions to file names, the idea being that we may have classes not laid out in a nice
		 * hierarchy and we don't want to read all files looking for package ...
		 */
		Map<String, FileInfo> nameMap = new HashMap<>();
		for (FileInfo fileInfo : fileInfos) {
			String path = fileInfo.getPath();
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

	private boolean validateSecret(String requestSecret, int installationId) {
		String digest = createInstallationHash(installationId);
		return (digest.equals(requestSecret));
	}

	private String createInstallationHash(int installationId) {
		long secret = getInstallationIdSecret();
		long value = installationId ^ secret;
		return sha1Digest(Long.toString(value));
	}

	private PrivateKey getApplicationKey() throws IOException {
		PrivateKey key = applicationKey;
		if (key != null) {
			return key;
		}

		synchronized (LambdaHandler.class) {
			key = applicationKey;
			if (key != null) {
				return key;
			}
			try {
				key = loadKey();
			} catch (GeneralSecurityException gse) {
				throw new IOException("problems processing pem key from ssm", gse);
			}
			applicationKey = key;
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
			throw new IllegalStateException("secret env value is null: " + INSTALLTION_ID_SECRET_ENV);
		}
		try {
			secret = Long.parseLong(secretStr);
		} catch (NumberFormatException nfe) {
			// we do't chain the exception to not expose the secret string in the logs
			throw new NumberFormatException("Could not parse long secret string");
		}
		installationIdSecret = secret;
		return secret;
	}

	private PrivateKey loadKey() throws IOException, GeneralSecurityException {
		String pem = System.getenv(PUBLISHER_PEM_ENV);
		if (pem == null) {
			throw new IOException("publisher secret env is null");
		}
		return KeyHandling.readKey(pem);
	}

	private static String sha1Digest(String str) {
		MessageDigest digest = createInstance(SHA1_ALGORITHM);
		digest.reset();
		digest.update(str.getBytes());
		return bytesToHex(digest.digest());
	}

	private static String bytesToHex(byte[] digest) {
		return new String(Hex.encodeHex(digest));
	}

	private static MessageDigest createInstance(String algorithm) {
		try {
			return MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException nsae) {
			throw new IllegalStateException("could not get " + algorithm + " instance", nsae);
		}
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
