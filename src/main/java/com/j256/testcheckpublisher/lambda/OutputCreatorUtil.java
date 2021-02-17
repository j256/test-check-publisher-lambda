package com.j256.testcheckpublisher.lambda;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckLevel;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunAnnotation;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunOutput;
import com.j256.testcheckpublisher.lambda.github.TreeInfoResponse.TreeFile;
import com.j256.testcheckpublisher.plugin.PublishedTestResults;
import com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResults;
import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult;
import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult.TestLevel;

/**
 * Utility class which isolates the formatting of our checks api request output.
 * 
 * @author graywatson
 */
public class OutputCreatorUtil {

	/**
	 * Create our output request.
	 */
	public static CheckRunOutput createOutput(LambdaLogger logger, PublishedTestResults publishedResults,
			Collection<TreeFile> treeFiles, Set<String> commitPathSet, String label) {

		String owner = publishedResults.getOwner();
		String repository = publishedResults.getRepository();
		String commitSha = publishedResults.getCommitSha();
		FrameworkTestResults frameworkResults = publishedResults.getResults();
		GithubFormat format = GithubFormat.fromString(publishedResults.getFormat());

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
			logger.log(label + ": ERROR: no framework results\n");
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
						logger.log(label + ": WARN: could not locate file associated with test path: "
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

		StringBuilder sb = new StringBuilder();
		appendNumber(sb, null, output.getTestCount(), "test", 's', true);
		appendNumber(sb, ", ", output.getFailureCount(), "failure", 's', true);
		appendNumber(sb, ", ", output.getErrorCount(), "error", 's', false);
		appendNumber(sb, ", ", frameworkResults.getNumSkipped(), "skipped", '\0', false);
		output.setTitle(sb.toString());
		output.setText(textSb.toString());

		return output;
	}

	private static void appendNumber(StringBuilder sb, String prefix, int num, String label, char pluralSuffix,
			boolean showAlways) {
		if (!showAlways && num == 0) {
			return;
		}
		if (prefix != null) {
			sb.append(prefix);
		}
		sb.append(num).append(' ').append(label);
		if (num != 1 && pluralSuffix != '\0') {
			sb.append(pluralSuffix);
		}
	}

	private static FileInfo mapFileByPath(Map<String, FileInfo> nameMap, String testPath) {

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

	private static void addTestResult(String owner, String repository, String commitSha, CheckRunOutput output,
			TestFileResult fileResult, FileInfo fileInfo, GithubFormat format, StringBuilder textSb) {

		TestLevel testLevel = fileResult.getTestLevel();
		if (testLevel == TestLevel.NOTICE && format.isNoPass()) {
			return;
		}
		CheckLevel level = CheckLevel.fromTestLevel(testLevel);

		if (!format.isNoAnnotate() && (fileInfo.isInCommit() || format.isAlwaysAnnotate())) {
			// always annotate even if the error isn't in commit
			CheckRunAnnotation annotation = new CheckRunAnnotation(fileInfo.getPath(), fileResult.getStartLineNumber(),
					fileResult.getEndLineNumber(), level, fileResult.getTestName(), fileResult.getMessage(),
					fileResult.getDetails());
			output.addAnnotation(annotation);
			return;
		}

		if (format.isNoDetails() || (testLevel == TestLevel.NOTICE && !format.isPassDetails())) {
			return;
		}

		/*
		 * The commit might make a change to a source file and fail a unit test that is not part of the commit. This
		 * results in effectively a broken link in the annotation file reference unfortunately. In this case we add some
		 * markdown into the details section at the top of the page.
		 */

		if (textSb.length() > 0) {
			// insert a horizontal line between the previous one and this one, newlines are needed
			textSb.append('\n');
			textSb.append("---\n");
			textSb.append('\n');
		}
		String emoji = EmojiUtils.levelToEmoji(testLevel, format);
		if (emoji != null) {
			textSb.append(emoji).append("&nbsp;&nbsp;");
		}
		textSb.append(testLevel.getPrettyString());
		textSb.append(": ");
		appendEscaped(textSb, fileResult.getTestName());
		textSb.append(": ");
		appendEscaped(textSb, fileResult.getMessage());
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
				.append(fileResult.getStartLineNumber())
				.append('\n');
		String details = fileResult.getDetails();
		if (!StringUtils.isBlank(details)) {
			// this seems to work although is brittle
			textSb.append("<details><summary>Raw output</summary>\n");
			textSb.append('\n');
			textSb.append("```\n");
			appendEscaped(textSb, details);
			if (!details.endsWith("\n")) {
				textSb.append('\n');
			}
			textSb.append("```\n");
			textSb.append("</details>\n");
		}
	}

	private static void appendEscaped(StringBuilder sb, String msg) {
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
}
