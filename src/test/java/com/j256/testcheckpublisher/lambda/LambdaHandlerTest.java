package com.j256.testcheckpublisher.lambda;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicStatusLine;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.j256.testcheckpublisher.lambda.ApiGatewayRequest.HttpContext;
import com.j256.testcheckpublisher.lambda.ApiGatewayRequest.RequestContext;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckLevel;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunAnnotation;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunOutput;
import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse;
import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse.ChangedFile;
import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse.Commit;
import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse.Tree;
import com.j256.testcheckpublisher.lambda.github.GithubClient;
import com.j256.testcheckpublisher.lambda.github.TreeInfoResponse.TreeFile;
import com.j256.testcheckpublisher.plugin.PublishedTestResults;
import com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResults;
import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult;
import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult.TestLevel;

public class LambdaHandlerTest {

	private static final ProtocolVersion HTTP_PROTOCOL_VERSION = new ProtocolVersion("HTTP", 1, 0);

	private final Gson gson = new GsonBuilder().create();

	@Test
	public void testStuff() throws IOException {
		LambdaHandler.setInstallationIdSecret(1234);
		LambdaHandler handler = new LambdaHandler();
		ApiGatewayRequest request = createRequest("/install", "installation_id=20", "body");
		ApiGatewayResponse response = doRequest(handler, request);
		assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		assertTrue(response.getBody(), response.getBody().contains("environment variable"));
	}

	@Test
	public void testNoSecretId() throws IOException {
		LambdaHandler.setInstallationIdSecret(0);
		LambdaHandler handler = new LambdaHandler();
		ApiGatewayRequest request = createRequest("/install", "installation_id=20", null);
		ApiGatewayResponse response = doRequest(handler, request);
		assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		assertTrue(response.getBody(), response.getBody().contains("server configuration"));
	}

	@Test
	public void testBadRequest() throws IOException {
		LambdaHandler.setInstallationIdSecret(1234);
		LambdaHandler handler = new LambdaHandler();
		ApiGatewayRequest request = createRequest("/install", "queryString", null);
		ApiGatewayResponse response = doRequest(handler, request);
		assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		assertTrue(response.getBody(), response.getBody().contains("not in the format"));
	}

	@Test
	public void testFile() throws IOException {
		LambdaHandler.setInstallationIdSecret(1234);
		LambdaHandler handler = new LambdaHandler();
		ApiGatewayRequest request = createRequest("/files/logo.png", null, null);
		ApiGatewayResponse response = doRequest(handler, request);
		assertEquals(HttpStatus.SC_OK, response.getStatusCode());
	}

	@Test
	public void testUnknownFile() throws IOException {
		LambdaHandler.setInstallationIdSecret(1234);
		LambdaHandler handler = new LambdaHandler();
		ApiGatewayRequest request = createRequest("/files/unknown", null, null);
		ApiGatewayResponse response = doRequest(handler, request);
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());
	}

	@Test
	public void testUpload() throws IOException, GeneralSecurityException {
		LambdaHandler.setInstallationIdSecret(1234);
		LambdaHandler.setApplicationKey(KeyHandling.readKey(readPrivateKey()));
		LambdaHandler handler = new LambdaHandler();

		GithubClient github = createMock(GithubClient.class);
		handler.setTestGithub(github);

		int numTests = 478;
		int numFailures = 11;
		int numErrors = 23;

		List<TestFileResult> testFileResults = new ArrayList<>();
		String filePath = "1/2/3.java";
		int startLine = 123213;
		TestLevel testLevel = TestLevel.ERROR;
		String title = "test123";
		String message = "message";
		String details = "details";
		testFileResults.add(new TestFileResult(filePath, startLine, TestLevel.ERROR, 0.1F, title, message, details));
		FrameworkTestResults frameworkResults =
				new FrameworkTestResults("name", numTests, numFailures, numErrors, testFileResults, "format");

		int installationId = 10;
		String hash = handler.createInstallationHash(null, installationId);

		String owner = "owner";
		String repo = "repo";
		String commitSha = "12345";
		PublishedTestResults results = new PublishedTestResults(owner, repo, commitSha, hash, frameworkResults);

		ApiGatewayRequest request = createRequest("/results", null, gson.toJson(results));

		expect(github.findInstallationId()).andReturn(installationId);
		expect(github.login()).andReturn(true);
		String treeSha = "446";
		Commit commit = new Commit(new Tree(treeSha));
		ChangedFile[] changesFiles = new ChangedFile[] { new ChangedFile(filePath, "added") };
		CommitInfoResponse commitResponse = new CommitInfoResponse(commitSha, commit, changesFiles);
		expect(github.requestCommitInfo(commitSha)).andReturn(commitResponse);
		List<TreeFile> treeFiles = new ArrayList<>();
		treeFiles.add(new TreeFile(filePath, "added", "filesha"));
		expect(github.requestTreeFiles(treeSha)).andReturn(treeFiles);

		List<CheckRunAnnotation> annotations = new ArrayList<>();
		annotations.add(new CheckRunAnnotation(filePath, startLine, startLine, CheckLevel.fromTestLevel(testLevel),
				title, message, details));
		CheckRunOutput output =
				new CheckRunOutput(numTests + " tests, " + numFailures + " failures, " + numErrors + " errors", "", "",
						annotations, numTests, numFailures, numErrors);
		CheckRunRequest checkRunRequest = new CheckRunRequest("name", commitSha, output);
		expect(github.addCheckRun(checkRunRequest)).andReturn(true);

		replay(github);

		ApiGatewayResponse response = doRequest(handler, request);
		assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		assertTrue(response.getBody(), response.getBody().contains("posted to github"));

		verify(github);
	}

	@Test
	public void testUploadNotInCommit() throws IOException, GeneralSecurityException {
		LambdaHandler.setInstallationIdSecret(1234);
		LambdaHandler.setApplicationKey(KeyHandling.readKey(readPrivateKey()));
		LambdaHandler handler = new LambdaHandler();

		GithubClient github = createMock(GithubClient.class);
		handler.setTestGithub(github);

		int numTests = 478;
		int numFailures = 11;
		int numErrors = 23;

		List<TestFileResult> testFileResults = new ArrayList<>();
		String filePath = "1/2/3.java";
		int startLine = 123213;
		TestLevel testLevel = TestLevel.ERROR;
		String title = "test123";
		String message = "message";
		String details = "details";
		testFileResults.add(new TestFileResult(filePath, startLine, TestLevel.ERROR, 0.1F, title, message, details));
		FrameworkTestResults frameworkResults =
				new FrameworkTestResults("name", numTests, numFailures, numErrors, testFileResults, "format");

		int installationId = 10;
		String hash = handler.createInstallationHash(null, installationId);

		String owner = "owner";
		String repo = "repo";
		String commitSha = "12345";
		PublishedTestResults results = new PublishedTestResults(owner, repo, commitSha, hash, frameworkResults);

		ApiGatewayRequest request = createRequest("/results", null, gson.toJson(results));

		expect(github.findInstallationId()).andReturn(installationId);
		expect(github.login()).andReturn(true);
		String treeSha = "446";
		Commit commit = new Commit(new Tree(treeSha));
		CommitInfoResponse commitResponse = new CommitInfoResponse(commitSha, commit, new ChangedFile[0]);
		expect(github.requestCommitInfo(commitSha)).andReturn(commitResponse);
		List<TreeFile> treeFiles = new ArrayList<>();
		treeFiles.add(new TreeFile(filePath, "added", "filesha"));
		expect(github.requestTreeFiles(treeSha)).andReturn(treeFiles);

		List<CheckRunAnnotation> annotations = new ArrayList<>();
		annotations.add(new CheckRunAnnotation(filePath, startLine, startLine, CheckLevel.fromTestLevel(testLevel),
				title, message, details));
		String text = "* " + testLevel.getPrettyString() + ": " + message + " https://github.com/" + owner + "/" + repo
				+ "/blob/" + commitSha + "/" + filePath + "#L" + startLine + "\n";
		CheckRunOutput output =
				new CheckRunOutput(numTests + " tests, " + numFailures + " failures, " + numErrors + " errors", "",
						text, annotations, numTests, numFailures, numErrors);
		CheckRunRequest checkRunRequest = new CheckRunRequest("name", commitSha, output);
		expect(github.addCheckRun(checkRunRequest)).andReturn(true);

		replay(github);

		ApiGatewayResponse response = doRequest(handler, request);
		assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		assertTrue(response.getBody(), response.getBody().contains("posted to github"));

		verify(github);
	}

	@Test
	public void testUploadCommitCallFailed() throws IOException, GeneralSecurityException {
		LambdaHandler.setInstallationIdSecret(1234);
		LambdaHandler.setApplicationKey(KeyHandling.readKey(readPrivateKey()));
		LambdaHandler handler = new LambdaHandler();

		GithubClient github = createMock(GithubClient.class);
		handler.setTestGithub(github);

		int numTests = 478;
		int numFailures = 11;
		int numErrors = 23;
		FrameworkTestResults frameworkResults =
				new FrameworkTestResults("name", numTests, numFailures, numErrors, Collections.emptyList(), "format");

		int installationId = 10;
		String hash = handler.createInstallationHash(null, installationId);

		String owner = "owner";
		String repo = "repo";
		String commitSha = "12345";
		PublishedTestResults results = new PublishedTestResults(owner, repo, commitSha, hash, frameworkResults);

		ApiGatewayRequest request = createRequest("/results", null, gson.toJson(results));

		expect(github.findInstallationId()).andReturn(installationId);
		expect(github.login()).andReturn(true);
		expect(github.requestCommitInfo(commitSha)).andReturn(null);
		expect(github.getLastStatusLine())
				.andReturn(new BasicStatusLine(HTTP_PROTOCOL_VERSION, HttpStatus.SC_FORBIDDEN, "forbidden"));

		replay(github);

		ApiGatewayResponse response = doRequest(handler, request);
		assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertTrue(response.getBody(), response.getBody().contains("not lookup commit"));

		verify(github);
	}

	@Test
	public void testUploadLoginFailed() throws IOException, GeneralSecurityException {
		LambdaHandler.setInstallationIdSecret(1234);
		LambdaHandler.setApplicationKey(KeyHandling.readKey(readPrivateKey()));
		LambdaHandler handler = new LambdaHandler();

		GithubClient github = createMock(GithubClient.class);
		handler.setTestGithub(github);

		int numTests = 478;
		int numFailures = 11;
		int numErrors = 23;
		FrameworkTestResults frameworkResults =
				new FrameworkTestResults("name", numTests, numFailures, numErrors, Collections.emptyList(), "format");

		int installationId = 10;
		String hash = handler.createInstallationHash(null, installationId);

		String owner = "owner";
		String repo = "repo";
		String commitSha = "12345";
		PublishedTestResults results = new PublishedTestResults(owner, repo, commitSha, hash, frameworkResults);

		ApiGatewayRequest request = createRequest("/results", null, gson.toJson(results));

		expect(github.findInstallationId()).andReturn(installationId);
		expect(github.login()).andReturn(false);
		expect(github.getLastStatusLine())
				.andReturn(new BasicStatusLine(HTTP_PROTOCOL_VERSION, HttpStatus.SC_FORBIDDEN, "forbidden"));

		replay(github);

		ApiGatewayResponse response = doRequest(handler, request);
		assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertTrue(response.getBody(), response.getBody().contains("not login to github"));

		verify(github);
	}

	@Test
	public void testUploadBadSecret() throws IOException, GeneralSecurityException {
		LambdaHandler.setInstallationIdSecret(1234);
		LambdaHandler.setApplicationKey(KeyHandling.readKey(readPrivateKey()));
		LambdaHandler handler = new LambdaHandler();

		GithubClient github = createMock(GithubClient.class);
		handler.setTestGithub(github);

		int numTests = 478;
		int numFailures = 11;
		int numErrors = 23;
		FrameworkTestResults frameworkResults =
				new FrameworkTestResults("name", numTests, numFailures, numErrors, Collections.emptyList(), "format");

		int installationId = 10;
		String hash = "not right";
		String owner = "owner";
		String repo = "repo";
		String commitSha = "12345";
		PublishedTestResults results = new PublishedTestResults(owner, repo, commitSha, hash, frameworkResults);

		ApiGatewayRequest request = createRequest("/results", null, gson.toJson(results));

		expect(github.findInstallationId()).andReturn(installationId);

		replay(github);

		ApiGatewayResponse response = doRequest(handler, request);
		assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		assertTrue(response.getBody(), response.getBody().contains("not validate"));

		verify(github);
	}

	@Test
	public void testUploadNoInstallationKey() throws IOException, GeneralSecurityException {
		LambdaHandler.setInstallationIdSecret(1234);
		LambdaHandler.setApplicationKey(KeyHandling.readKey(readPrivateKey()));
		LambdaHandler handler = new LambdaHandler();

		GithubClient github = createMock(GithubClient.class);
		handler.setTestGithub(github);

		int numTests = 478;
		int numFailures = 11;
		int numErrors = 23;
		FrameworkTestResults frameworkResults =
				new FrameworkTestResults("name", numTests, numFailures, numErrors, Collections.emptyList(), "format");

		int installationId = 10;
		String hash = handler.createInstallationHash(null, installationId);

		String owner = "owner";
		String repo = "repo";
		String commitSha = "12345";
		PublishedTestResults results = new PublishedTestResults(owner, repo, commitSha, hash, frameworkResults);

		ApiGatewayRequest request = createRequest("/results", null, gson.toJson(results));

		expect(github.findInstallationId()).andReturn(-1);

		replay(github);

		ApiGatewayResponse response = doRequest(handler, request);
		assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		assertTrue(response.getBody(), response.getBody().contains("not find installation"));

		verify(github);
	}

	@Test
	public void testUploadNoApplicationKey() throws IOException {
		LambdaHandler.setInstallationIdSecret(1234);
		LambdaHandler.setApplicationKey(null);
		LambdaHandler handler = new LambdaHandler();

		GithubClient github = createMock(GithubClient.class);
		handler.setTestGithub(github);

		int numTests = 478;
		int numFailures = 11;
		int numErrors = 23;
		FrameworkTestResults frameworkResults =
				new FrameworkTestResults("name", numTests, numFailures, numErrors, Collections.emptyList(), "format");

		int installationId = 10;
		String hash = handler.createInstallationHash(null, installationId);

		String owner = "owner";
		String repo = "repo";
		String commitSha = "12345";
		PublishedTestResults results = new PublishedTestResults(owner, repo, commitSha, hash, frameworkResults);

		ApiGatewayRequest request = createRequest("/results", null, gson.toJson(results));

		replay(github);

		ApiGatewayResponse response = doRequest(handler, request);
		assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());

		verify(github);
	}

	@Test
	public void testCoverage() throws IOException {
		LambdaHandler.setInstallationIdSecret(1234);
		LambdaHandler handler = new LambdaHandler();
		doRequest(handler, createRequest(null, null, null));
		doRequest(handler, createRequest("", null, null));
		assertEquals(HttpStatus.SC_NOT_FOUND,
				doRequest(handler, createRequest("/test/foo", null, null)).getStatusCode());
		assertEquals(HttpStatus.SC_NOT_FOUND,
				doRequest(handler, createRequest("/prod/foo", null, null)).getStatusCode());
		assertEquals(HttpStatus.SC_MOVED_PERMANENTLY,
				doRequest(handler, createRequest("/results", null, null)).getStatusCode());
		assertEquals(HttpStatus.SC_MOVED_PERMANENTLY,
				doRequest(handler, createRequest("/results", null, "")).getStatusCode());
		assertEquals(HttpStatus.SC_BAD_REQUEST,
				doRequest(handler, createRequest("/results", null, "bad-content")).getStatusCode());
		doRequest(handler, createRequest("/results", null, "not base 64 !!!~^__ZZzz", true));
	}

	private ApiGatewayRequest createRequest(String rawPath, String rawQueryString, String body) {
		return createRequest(rawPath, rawQueryString, body, false);
	}

	private ApiGatewayRequest createRequest(String rawPath, String rawQueryString, String body,
			boolean isBodyBase64Encoded) {
		HttpContext httpContext = new HttpContext("method", rawPath, "source-ip", "agent");
		RequestContext requestContext = new RequestContext("domain", "request-id", httpContext);
		return new ApiGatewayRequest(rawPath, rawQueryString, Collections.emptyMap(), requestContext, body,
				isBodyBase64Encoded);
	}

	private ApiGatewayResponse doRequest(LambdaHandler handler, ApiGatewayRequest request) throws IOException {

		StringWriter writer = new StringWriter();
		gson.toJson(request, writer);
		ByteArrayInputStream bais = new ByteArrayInputStream(writer.toString().getBytes());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		handler.handleRequest(bais, baos, new ContextImpl());

		ApiGatewayResponse response = gson.fromJson(new String(baos.toByteArray()), ApiGatewayResponse.class);
		assertNotNull(response);
		return response;
	}

	private String readPrivateKey() throws IOException {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream("fake_key.pem");) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			while (true) {
				int numRead = input.read(buf);
				if (numRead < 0) {
					break;
				}
				baos.write(buf, 0, numRead);
			}
			return new String(baos.toByteArray());
		}

	}
}
