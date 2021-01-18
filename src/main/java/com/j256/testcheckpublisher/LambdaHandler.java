package com.j256.testcheckpublisher;

import java.io.File;
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
import com.j256.testcheckpublisher.frameworks.FrameworkTestResults;
import com.j256.testcheckpublisher.frameworks.FrameworkTestResults.TestFileResult;
import com.j256.testcheckpublisher.github.CheckRunRequest;
import com.j256.testcheckpublisher.github.CheckRunRequest.CheckLevel;
import com.j256.testcheckpublisher.github.CheckRunRequest.CheckRunAnnotation;
import com.j256.testcheckpublisher.github.CheckRunRequest.CheckRunOutput;
import com.j256.testcheckpublisher.github.CommitInfoResponse;
import com.j256.testcheckpublisher.github.CommitInfoResponse.ChangedFile;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Main lambda handler.
 * 
 * @author graywatson
 */
public class LambdaHandler implements RequestStreamHandler {

	private static final String PUBLISHER_SECRET_PARAM = "github_test_check_publisher_secret";
	private static final String INSTALLTION_ID_SECRET_ENV = "installation_id_secret";
	private static final String SHA1_ALGORITHM = "SHA1";

	// XXX: need an old one too right?
	private static volatile PrivateKey applicationKey;
	// XXX: need an old one too right?
	private static volatile long installationIdSecret;

	@Override
	public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
		LambdaLogger logger = context.getLogger();

		// read in request of files, sha, repo, secret

		InputStreamReader reader = new InputStreamReader(inputStream);
		Gson gson = new GsonBuilder().create();

		// PublishedTestResults results = new Gson().fromJson(reader, PublishedTestResults.class);
		ApiGatewayRequest request = gson.fromJson(reader, ApiGatewayRequest.class);
		String body = request.getBody();

		// the body is probably base64 encoded because it is json payload
		if (request.isBodyBase64Encoded()) {
			body = new String(Base64.decodeBase64(body));
		}

		PublishedTestResults results = gson.fromJson(body, PublishedTestResults.class);
		if (!results.isMagicCorrect()) {
			// request sanity check failed
			logger.log("request sanity check failed: " + results.getMagic() + "\n");
			writeResponse(outputStream, gson, HttpStatus.SC_BAD_REQUEST, "Posted request is invalid");
			return;
		}
		String repository = results.getRepository();

		logger.log(results.getOwner() + "/" + repository + "@" + results.getCommitSha() + "\n");

		CloseableHttpClient httpclient = HttpClients.createDefault();

		GithubClient github = new GithubClient(httpclient, results.getOwner(), repository, getApplicationKey(), logger);

		int installationId = github.findInstallationId();
		if (installationId <= 0) {
			logger.log(repository + ": no installation-id\n");
			writeResponse(outputStream, gson, HttpStatus.SC_NOT_FOUND,
					"Could not find installation for application in repository " + repository
							+ ", reinstall integration");
			return;
		}

		if (!validateSecret(outputStream, results.getSecret(), installationId)) {
			logger.log(repository + ": secret did not validate\n");
			writeResponse(outputStream, gson, HttpStatus.SC_FORBIDDEN,
					"Secret did not validate, reinstall integration");
			return;
		}

		CommitInfoResponse commitInfo = github.requestCommitInfo(results.getCommitSha());
		if (commitInfo == null) {
			writeResponse(outputStream, gson, HttpStatus.SC_INTERNAL_SERVER_ERROR,
					"Could not lookup commit information");
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

		Collection<FileInfo> fileInfos = github.requestTreeFiles(commitInfo.getTreeSha());
		for (FileInfo fileInfo : fileInfos) {
			if (commitPathSet.contains(fileInfo.path)) {
				fileInfo.setInCommit(true);
			}
		}

		CheckRunOutput output = createRequest(results, fileInfos);
		CheckRunRequest checkRunRequest =
				new CheckRunRequest(results.getResults().getName(), results.getCommitSha(), output);

		github.addCheckRun(checkRunRequest);

		logger.log(repository + ": posted check-run " + output.getTitle() + "\n");
		writeResponse(outputStream, gson, HttpStatus.SC_OK, "check-run posted to github");
	}

	private void writeResponse(OutputStream outputStream, Gson gson, int statusCode, String message)
			throws IOException {
		Map<String, String> headerMap = Collections.singletonMap("Content-Type", "text/plain");
		ApiGatewayResponse response =
				new ApiGatewayResponse(HttpStatus.SC_OK, null, headerMap, "check-run posted to github", false);
		try (Writer writer = new OutputStreamWriter(outputStream);) {
			gson.toJson(response, writer);
		}
	}

	private CheckRunOutput createRequest(PublishedTestResults results, Collection<FileInfo> fileInfos) {

		String owner = results.getOwner();
		String repository = results.getRepository();
		String commitSha = results.getCommitSha();

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
				int nextIndex = path.indexOf(File.separatorChar, index);
				if (nextIndex < 0) {
					break;
				}
				index = nextIndex + 1;
				nameMap.put(path.substring(index), fileInfo);
			}
			// should be just the name
			nameMap.put(path.substring(index), fileInfo);
		}

		StringBuilder textSb = new StringBuilder();

		FrameworkTestResults frameworkResults = results.getResults();
		if (frameworkResults != null && frameworkResults.getFileResults() != null) {
			for (TestFileResult fileResult : frameworkResults.getFileResults()) {
				FileInfo fileInfo = mapFileByPath(nameMap, fileResult.getPath());
				if (fileInfo == null) {
					// XXX: could not locate this file
					System.err.println(
							"WARNING: could not locate file associated with test path: " + fileResult.getPath());
				} else {
					addTestResult(owner, repository, commitSha, output, fileResult, fileInfo, textSb);
				}
			}
		}

		String title = output.getTestCount() + " tests, " + output.getErrorCount() + " errors, "
				+ output.getFailureCount() + " failures";
		output.setTitle(title);
		output.setText(textSb.toString());

		return output;
	}

	/**
	 * XXX: Maybe just use substring or endswith here because it's all about paths (at least in java)
	 */
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
		// should be just the name
		return nameMap.get(testPath.substring(index));
	}

	private void addTestResult(String owner, String repository, String commitSha, CheckRunOutput output,
			TestFileResult fileResult, FileInfo fileInfo, StringBuilder textSb) {

		CheckLevel level = CheckLevel.fromTestLevel(fileResult.getTestLevel());

		output.addAnnotation(
				new CheckRunAnnotation(fileInfo.getPath(), fileResult.getLineNumber(), fileResult.getLineNumber(),
						level, fileResult.getTestName(), fileResult.getMessage(), fileResult.getDetails()));

		/*
		 * If the file is not referenced in the commit then we add into the text of the check a reference to it. You
		 * might check in a change to a source file and fail a unit test not mentioned in the commit.
		 */
		if (!fileInfo.isInCommit()) {
			textSb.append("* ")
					.append(fileResult.getMessage())
					.append(' ')
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
					.append("\n");
		}
	}

	private boolean validateSecret(OutputStream outputStream, String requestSecret, int installationId) {

		long secret = getInstallationIdSecret();
		long value = installationId ^ secret;
		String digest = sha1Digest(Long.toString(value));

		// XXX: test old secret as well
		return (digest.equals(requestSecret));
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
			throw new RuntimeException("secret env value is null: " + INSTALLTION_ID_SECRET_ENV);
		}
		try {
			secret = Long.parseLong(secretStr);
		} catch (Exception e) {
			// we do this to not expose the secret in the logs
			throw new RuntimeException("Could not parse long secret string");
		}
		installationIdSecret = secret;
		return secret;
	}

	private PrivateKey loadKey() throws IOException, GeneralSecurityException {
		// lookup parameter
		SsmClient ssmClient = SsmClient.builder().build();
		GetParameterResponse result =
				ssmClient.getParameter(GetParameterRequest.builder().name(PUBLISHER_SECRET_PARAM).build());
		if (result == null) {
			throw new IOException("ssmClient.getParameter() returned null");
		} else if (result.parameter() == null) {
			throw new IOException("ssmClient.getParameter().parameter() is null");
		}
		String pem = result.parameter().value();
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
			throw new RuntimeException("could not get " + algorithm + " instance", nsae);
		}
	}
}
