package com.j256.testcheckpublisher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.binary.Hex;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.google.gson.Gson;
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
		PublishedTestResults results = new Gson().fromJson(reader, PublishedTestResults.class);
		if (results.isMagicCorrect()) {
			throw new IllegalStateException("posted request is invalid");
		}

		logger.log(results.getOwner() + "/" + results.getRepository() + "@" + results.getCommitSha() + "\n");

		CloseableHttpClient httpclient = HttpClients.createDefault();

		GithubClient github =
				new GithubClient(httpclient, results.getOwner(), results.getRepository(), getApplicationKey());

		validateSecret(outputStream, results.getSecret(), github);

		CommitInfoResponse commitInfo = github.requestCommitInfo(results.getCommitSha());
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

		CheckRunOutput output =
				createRequest(results.getOwner(), results.getRepository(), results.getCommitSha(), fileInfos);
		CheckRunRequest request = new CheckRunRequest(results.getResults().getName(), results.getCommitSha(), output);

		github.addCheckRun(request);

		outputStream.write(("{ \"response\" : \"check-run posted to github\" }").getBytes());
	}

	private CheckRunOutput createRequest(String owner, String repository, String commitSha,
			Collection<FileInfo> fileInfos) {

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

		FrameworkTestResults results = new FrameworkTestResults(100);

		for (TestFileResult fileResult : results.getFileResults()) {

			FileInfo fileInfo = mapFileByClass(nameMap, fileResult.getPath());
			if (fileInfo == null) {
				// XXX: could not locate this file
				System.err.println("WARNING: could not locate file associated with test path: " + fileResult.getPath());
			} else {
				addTestResult(owner, repository, commitSha, output, fileResult, fileInfo, textSb);
			}
		}

		String title = output.getTestCount() + " tests, " + output.getErrorCount() + " errors, "
				+ output.getFailureCount() + " failures";
		output.setTitle(title);
		output.setText(textSb.toString());

		return output;
	}

	private FileInfo mapFileByClass(Map<String, FileInfo> nameMap, String testPath) {

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

	private void validateSecret(OutputStream outputStream, String requestSecret, GithubClient github)
			throws IOException {
		int installationId = github.findInstallationId();
		long secret = getInstallationIdSecret();

		long value = installationId ^ secret;
		String digest = sha1Digest(Long.toString(value));
		// XXX: test old secret as well
		if (!digest.equals(requestSecret)) {
			outputStream.write(
					("{ \"response\" : \"Invalid secret.  Is your test-check-publisher env variable set right?\" }")
							.getBytes());
		}
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
