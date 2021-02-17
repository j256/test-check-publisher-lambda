package com.j256.testcheckpublisher.lambda;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import com.amazonaws.services.lambda.runtime.LambdaRuntime;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckLevel;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunAnnotation;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunOutput;
import com.j256.testcheckpublisher.lambda.github.TreeInfoResponse.TreeFile;
import com.j256.testcheckpublisher.plugin.PublishedTestResults;
import com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResults;
import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult;
import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult.TestLevel;

public class OutputCreatorUtilTest {

	@Test
	public void testUpload() {

		int numTests = 478;
		int numFailures = 11;
		int numErrors = 1;
		int numSkipped = 34;

		List<TestFileResult> testFileResults = new ArrayList<>();
		String filePath = "1/2/3.java";
		int startLine = 123213;
		String testName = "test123";
		String message = "message";
		String details = "details";
		TestLevel testLevel = TestLevel.ERROR;
		testFileResults
				.add(new TestFileResult(filePath, startLine, startLine, testLevel, 0.1F, testName, message, details));
		String formatStr = "format";
		FrameworkTestResults frameworkResults =
				new FrameworkTestResults("name", numTests, numFailures, numErrors, numSkipped, testFileResults);

		String owner = "owner";
		String repo = "repo";
		String commitSha = "12345";
		PublishedTestResults results =
				new PublishedTestResults(owner, repo, commitSha, "secret", formatStr, frameworkResults);

		List<TreeFile> treeFiles = new ArrayList<>();
		treeFiles.add(new TreeFile(filePath, "added", "filesha"));

		CheckRunOutput output = OutputCreatorUtil.createOutput(LambdaRuntime.getLogger(), results, treeFiles,
				new HashSet<String>(), "test");
		GithubFormat format = GithubFormat.fromString(formatStr);

		String title =
				numTests + " tests, " + numFailures + " failures, " + numErrors + " error, " + numSkipped + " skipped";
		assertEquals(title, output.getTitle());
		assertEquals(numTests, output.getTestCount());
		assertEquals(numFailures, output.getFailureCount());
		assertEquals(numErrors, output.getErrorCount());
		String text = EmojiUtils.levelToEmoji(testLevel, format) + "&nbsp;&nbsp;" //
				+ testLevel.getPrettyString() + ": " + testName + ": " + message + " " //
				+ "https://github.com/" + owner + "/" + repo + "/blob/" + commitSha + "/" + filePath + "#L" + startLine
				+ "\n" //
				+ "<details><summary>Raw output</summary>\n" //
				+ "\n" //
				+ "```\n" //
				+ details + "\n" //
				+ "```\n" //
				+ "</details>\n";
		assertEquals(text, output.getText());
	}

	@Test
	public void testUploadNotInCommit() {

		int numTests = 478;
		int numFailures = 11;
		int numErrors = 0;
		int numSkipped = 0;

		List<TestFileResult> testFileResults = new ArrayList<>();
		String filePath = "1/2/3.java";
		int startLine = 123213;
		TestLevel testLevel = TestLevel.ERROR;
		String testName = "test123";
		String message = "message";
		String details = "details\nhere";
		String formatStr = "format";
		testFileResults.add(
				new TestFileResult(filePath, startLine, startLine, TestLevel.ERROR, 0.1F, testName, message, details));
		FrameworkTestResults frameworkResults =
				new FrameworkTestResults("name", numTests, numFailures, numErrors, numSkipped, testFileResults);

		GithubFormat format = GithubFormat.fromString(formatStr);
		String owner = "owner";
		String repo = "repo";
		String commitSha = "12345";
		PublishedTestResults results =
				new PublishedTestResults(owner, repo, commitSha, "secret", formatStr, frameworkResults);

		List<TreeFile> treeFiles = new ArrayList<>();
		treeFiles.add(new TreeFile(filePath, "added", "filesha"));

		List<CheckRunAnnotation> annotations = new ArrayList<>();
		annotations.add(new CheckRunAnnotation(filePath, startLine, startLine, CheckLevel.fromTestLevel(testLevel),
				testName, message, details));

		CheckRunOutput output = OutputCreatorUtil.createOutput(LambdaRuntime.getLogger(), results, treeFiles,
				new HashSet<String>(), "test");

		String title = numTests + " tests, " + numFailures + " failures";
		assertEquals(title, output.getTitle());
		assertEquals(numTests, output.getTestCount());
		assertEquals(numFailures, output.getFailureCount());
		assertEquals(numErrors, output.getErrorCount());
		String text = EmojiUtils.levelToEmoji(testLevel, format) + "&nbsp;&nbsp;" //
				+ testLevel.getPrettyString() + ": " + testName + ": " + message + " " //
				+ "https://github.com/" + owner + "/" + repo + "/blob/" + commitSha + "/" + filePath + "#L" + startLine
				+ "\n" //
				+ "<details><summary>Raw output</summary>\n" //
				+ "\n" //
				+ "```\n" //
				+ details + "\n" //
				+ "```\n" //
				+ "</details>\n";
		assertEquals(text, output.getText());
	}

	@Test
	public void testUploadEscapedChars() {

		List<TestFileResult> testFileResults = new ArrayList<>();
		String filePath = "1/2/3.java";
		int startLine = 123213;
		TestLevel testLevel = TestLevel.ERROR;
		String testName = "test123";
		String message = "message <special> & characters";
		String messageEscaped = "message &lt;special&gt; &amp; characters";
		String details = "details\nhere";
		String formatStr = "format";
		testFileResults.add(
				new TestFileResult(filePath, startLine, startLine, TestLevel.ERROR, 0.1F, testName, message, details));
		FrameworkTestResults frameworkResults = new FrameworkTestResults("name", 1, 2, 3, 4, testFileResults);

		GithubFormat format = GithubFormat.fromString(formatStr);
		String owner = "owner";
		String repo = "repo";
		String commitSha = "12345";
		PublishedTestResults results =
				new PublishedTestResults(owner, repo, commitSha, "secret", formatStr, frameworkResults);

		List<TreeFile> treeFiles = new ArrayList<>();
		treeFiles.add(new TreeFile(filePath, "added", "filesha"));

		List<CheckRunAnnotation> annotations = new ArrayList<>();
		annotations.add(new CheckRunAnnotation(filePath, startLine, startLine, CheckLevel.fromTestLevel(testLevel),
				testName, message, details));

		CheckRunOutput output = OutputCreatorUtil.createOutput(LambdaRuntime.getLogger(), results, treeFiles,
				new HashSet<String>(), "test");

		String text = EmojiUtils.levelToEmoji(testLevel, format) + "&nbsp;&nbsp;" //
				+ testLevel.getPrettyString() + ": " + testName + ": " + messageEscaped + " " //
				+ "https://github.com/" + owner + "/" + repo + "/blob/" + commitSha + "/" + filePath + "#L" + startLine
				+ "\n" //
				+ "<details><summary>Raw output</summary>\n" //
				+ "\n" //
				+ "```\n" //
				+ details + "\n" //
				+ "```\n" //
				+ "</details>\n";
		assertEquals(text, output.getText());
	}

	@Test
	public void testTestAndTreePathMismatch() {

		List<TestFileResult> testFileResults = new ArrayList<>();
		String filePath = "a\\b\\3.java";
		int startLine = 123213;
		TestLevel testLevel = TestLevel.ERROR;
		String testName = "test123";
		String message = "message <special> & characters";
		String messageEscaped = "message &lt;special&gt; &amp; characters";
		String details = "details\nhere";
		String formatStr = "format";
		testFileResults.add(
				new TestFileResult(filePath, startLine, startLine, TestLevel.ERROR, 0.1F, testName, message, details));
		FrameworkTestResults frameworkResults = new FrameworkTestResults("name", 1, 2, 3, 4, testFileResults);

		GithubFormat format = GithubFormat.fromString(formatStr);
		String owner = "owner";
		String repo = "repo";
		String commitSha = "12345";
		PublishedTestResults results =
				new PublishedTestResults(owner, repo, commitSha, "secret", formatStr, frameworkResults);

		List<TreeFile> treeFiles = new ArrayList<>();
		String treePath = "some/other/path/b/3.java";
		treeFiles.add(new TreeFile(treePath, "added", "filesha"));

		List<CheckRunAnnotation> annotations = new ArrayList<>();
		annotations.add(new CheckRunAnnotation(filePath, startLine, startLine, CheckLevel.fromTestLevel(testLevel),
				testName, message, details));

		CheckRunOutput output = OutputCreatorUtil.createOutput(LambdaRuntime.getLogger(), results, treeFiles,
				new HashSet<String>(), "test");

		String text = EmojiUtils.levelToEmoji(testLevel, format) + "&nbsp;&nbsp;" //
				+ testLevel.getPrettyString() + ": " + testName + ": " + messageEscaped + " " //
				+ "https://github.com/" + owner + "/" + repo + "/blob/" + commitSha + "/" + treePath + "#L" + startLine
				+ "\n" //
				+ "<details><summary>Raw output</summary>\n" //
				+ "\n" //
				+ "```\n" //
				+ details + "\n" //
				+ "```\n" //
				+ "</details>\n";
		assertEquals(text, output.getText());
	}
}
