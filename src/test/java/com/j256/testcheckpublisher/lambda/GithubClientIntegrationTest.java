package com.j256.testcheckpublisher.lambda;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import com.amazonaws.services.lambda.runtime.LambdaRuntime;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunOutput;
import com.j256.testcheckpublisher.lambda.github.GithubClient;
import com.j256.testcheckpublisher.lambda.github.GithubClientImpl;
import com.j256.testcheckpublisher.lambda.github.TreeInfoResponse.TreeFile;
import com.j256.testcheckpublisher.plugin.PublishedTestResults;
import com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResults;
import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult;
import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult.TestLevel;

public class GithubClientIntegrationTest {

	private static final String APP_ID_ENV_NAME = "APP_ID";
	private static final String PEM_PATH_ENV_NAME = "PEM_PATH";

	private static final String FILE_PATH1 =
			"src/test/java/com/j256/testcheckpublisher/plugin/frameworks/TestFileResultTest.java";
	private static final String FILE_PATH2 =
			"src/test/java/com/j256/testcheckpublisher/plugin/frameworks/FrameworkTestResultsTest.java";

	public static void main(String[] args) throws Exception {

		String appIdStr = System.getenv(APP_ID_ENV_NAME);
		assertNotNull(appIdStr);
		String pemPath = System.getenv(PEM_PATH_ENV_NAME);
		assertNotNull(pemPath);

		PrivateKey key = KeyHandling.loadKey(pemPath);

		GithubClientImpl.setGithubAppId(appIdStr);
		CloseableHttpClient httpClient = HttpClientBuilder.create().build();
		GithubClient githubClient = GithubClientImpl.createClient(httpClient, "j256",
				"test-check-publisher-maven-plugin", key, LambdaRuntime.getLogger(), "test");

		assertNotEquals(-1, githubClient.findInstallationId());
		assertTrue(githubClient.login());

		List<TreeFile> treeFiles = new ArrayList<>();
		treeFiles.add(new TreeFile(FILE_PATH1, "file", "sha"));
		treeFiles.add(new TreeFile(FILE_PATH2, "file", "sha"));
		Set<String> commitPathSet = new HashSet<>();
		commitPathSet.add(FILE_PATH2);

		PublishedTestResults publishedResults = createResults();
		CheckRunOutput output = OutputCreatorUtil.createOutput(LambdaRuntime.getLogger(), publishedResults, treeFiles,
				commitPathSet, "test");

		System.out.println("Making request...");
		CheckRunRequest request =
				new CheckRunRequest("Surefire test results", "eb8fae1ccc4411d0139507c26446fc41ac867c71", output);
		assertTrue(githubClient.addCheckRun(request));
		System.out.println("Done...");
	}

	private static PublishedTestResults createResults() {
		int numTests = 23;
		int numFailures = 2;
		int numErrors = 1;
		int numSkipped = 0;

		List<TestFileResult> testFileResults = new ArrayList<>();
		int startLine1 = 115;
		String testName1 = "com.j256.testcheckpublisher.plugin.frameworks.TestFileResultTest.testStuff";
		String message1 = "Values should be different. Actual: 1431808913";
		String details1 = "java.lang.AssertionError: Values should be different. Actual: 1431808913\n"
				+ "	at org.junit.Assert.fail(Assert.java:89)\n" + "	at org.junit.Assert.failEquals(Assert.java:187)\n"
				+ "	at org.junit.Assert.assertNotEquals(Assert.java:201)\n"
				+ "	at org.junit.Assert.assertNotEquals(Assert.java:213)\n"
				+ "	at com.j256.testcheckpublisher.plugin.frameworks.TestFileResultTest.testNotEquals(TestFileResultTest.java:115)\n"
				+ "	at com.j256.testcheckpublisher.plugin.frameworks.TestFileResultTest.testStuff(TestFileResultTest.java:48)\n"
				+ "	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n"
				+ "	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\n"
				+ "	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n"
				+ "	at java.lang.reflect.Method.invoke(Method.java:498)\n"
				+ "	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)\n"
				+ "	at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\n"
				+ "	at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:56)\n"
				+ "	at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\n"
				+ "	at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)\n"
				+ "	at org.junit.runners.BlockJUnit4ClassRunner$1.evaluate(BlockJUnit4ClassRunner.java:100)\n"
				+ "	at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:366)\n"
				+ "	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:103)\n"
				+ "	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:63)\n"
				+ "	at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)\n"
				+ "	at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)\n"
				+ "	at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)\n"
				+ "	at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)\n"
				+ "	at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)\n"
				+ "	at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)\n"
				+ "	at org.junit.runners.ParentRunner.run(ParentRunner.java:413)\n"
				+ "	at org.apache.maven.surefire.junit4.JUnit4Provider.execute(JUnit4Provider.java:367)\n"
				+ "	at org.apache.maven.surefire.junit4.JUnit4Provider.executeWithRerun(JUnit4Provider.java:274)\n"
				+ "	at org.apache.maven.surefire.junit4.JUnit4Provider.executeTestSet(JUnit4Provider.java:238)\n"
				+ "	at org.apache.maven.surefire.junit4.JUnit4Provider.invoke(JUnit4Provider.java:161)\n"
				+ "	at org.apache.maven.surefire.booter.ForkedBooter.invokeProviderInSameClassLoader(ForkedBooter.java:290)\n"
				+ "	at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:242)\n"
				+ "	at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:121)\n";
		testFileResults.add(new TestFileResult(FILE_PATH1, startLine1, startLine1, TestLevel.FAILURE, 0.1F, testName1,
				message1, details1));

		int startLine2 = 21;
		String testName2 = "com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResultsTest.testDoesntCompile";
		String message2 = "java.lang.Error: simulate an error";
		String details2 = "java.lang.Error: simulate an error\n"
				+ "	at com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResultsTest.testDoesntCompile(FrameworkTestResultsTest.java:21)\n"
				+ "	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n"
				+ "	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\n"
				+ "	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n"
				+ "	at java.lang.reflect.Method.invoke(Method.java:498)\n"
				+ "	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)\n"
				+ "	at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\n"
				+ "	at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:56)\n"
				+ "	at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\n"
				+ "	at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)\n"
				+ "	at org.junit.runners.BlockJUnit4ClassRunner$1.evaluate(BlockJUnit4ClassRunner.java:100)\n"
				+ "	at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:366)\n"
				+ "	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:103)\n"
				+ "	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:63)\n"
				+ "	at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)\n"
				+ "	at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)\n"
				+ "	at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)\n"
				+ "	at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)\n"
				+ "	at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)\n"
				+ "	at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)\n"
				+ "	at org.junit.runners.ParentRunner.run(ParentRunner.java:413)\n"
				+ "	at org.apache.maven.surefire.junit4.JUnit4Provider.execute(JUnit4Provider.java:367)\n"
				+ "	at org.apache.maven.surefire.junit4.JUnit4Provider.executeWithRerun(JUnit4Provider.java:274)\n"
				+ "	at org.apache.maven.surefire.junit4.JUnit4Provider.executeTestSet(JUnit4Provider.java:238)\n"
				+ "	at org.apache.maven.surefire.junit4.JUnit4Provider.invoke(JUnit4Provider.java:161)\n"
				+ "	at org.apache.maven.surefire.booter.ForkedBooter.invokeProviderInSameClassLoader(ForkedBooter.java:290)\n"
				+ "	at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:242)\n"
				+ "	at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:121)\n";
		testFileResults.add(new TestFileResult(FILE_PATH2, startLine2, startLine2, TestLevel.FAILURE, 0.1F, testName2,
				message2, details2));

		int startLine3 = 16;
		String testName3 = "com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResultsTest.testFail";
		String message3 = "java.lang.AssertionError: Showing a test failure for a test in the commit";
		String details3 = "java.lang.AssertionError: Showing a test failure for a test in the commit\n"
				+ "	at org.junit.Assert.fail(Assert.java:89)\n"
				+ "	at com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResultsTest.testFail(FrameworkTestResultsTest.java:16)\n"
				+ "	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n"
				+ "	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\n"
				+ "	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n"
				+ "	at java.lang.reflect.Method.invoke(Method.java:498)\n"
				+ "	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)\n"
				+ "	at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\n"
				+ "	at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:56)\n"
				+ "	at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\n"
				+ "	at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)\n"
				+ "	at org.junit.runners.BlockJUnit4ClassRunner$1.evaluate(BlockJUnit4ClassRunner.java:100)\n"
				+ "	at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:366)\n"
				+ "	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:103)\n"
				+ "	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:63)\n"
				+ "	at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)\n"
				+ "	at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)\n"
				+ "	at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)\n"
				+ "	at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)\n"
				+ "	at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)\n"
				+ "	at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)\n"
				+ "	at org.junit.runners.ParentRunner.run(ParentRunner.java:413)\n"
				+ "	at org.apache.maven.surefire.junit4.JUnit4Provider.execute(JUnit4Provider.java:367)\n"
				+ "	at org.apache.maven.surefire.junit4.JUnit4Provider.executeWithRerun(JUnit4Provider.java:274)\n"
				+ "	at org.apache.maven.surefire.junit4.JUnit4Provider.executeTestSet(JUnit4Provider.java:238)\n"
				+ "	at org.apache.maven.surefire.junit4.JUnit4Provider.invoke(JUnit4Provider.java:161)\n"
				+ "	at org.apache.maven.surefire.booter.ForkedBooter.invokeProviderInSameClassLoader(ForkedBooter.java:290)\n"
				+ "	at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:242)\n"
				+ "	at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:121)\n";
		testFileResults.add(new TestFileResult(FILE_PATH2, startLine3, startLine3, TestLevel.FAILURE, 0.1F, testName3,
				message3, details3));

		int startLine4 = 79;
		String testName4 = "com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResultsTest.testToString";
		String message4 = "succeeded, no errors";
		testFileResults.add(new TestFileResult(FILE_PATH2, startLine4, startLine4, TestLevel.NOTICE, 0.1F, testName4,
				message4, null));

		FrameworkTestResults frameworkResults =
				new FrameworkTestResults("name", numTests, numFailures, numErrors, numSkipped, testFileResults);

		String owner = "j256";
		String repo = "test-check-publisher-maven-plugin";
		String commitSha = "eb8fae1ccc4411d0139507c26446fc41ac867c71";
		String formatStr = "noannotate,passdetails";
		PublishedTestResults results =
				new PublishedTestResults(owner, repo, commitSha, "secret", formatStr, frameworkResults);
		return results;
	}
}
