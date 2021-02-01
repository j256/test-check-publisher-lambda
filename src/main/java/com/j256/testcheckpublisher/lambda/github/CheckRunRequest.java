package com.j256.testcheckpublisher.lambda.github;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * Information about a check-run that is posted to github. https://docs.github.com/en/rest/reference/checks#runs
 * 
 * @author graywatson
 */
public class CheckRunRequest {

	final String name;
	@SerializedName("head_sha")
	final String sha;
	final CheckRunOutput output;
	final Status status;
	final Conclusion conclusion;

	public CheckRunRequest(String name, String sha, CheckRunOutput output) {
		this.name = name;
		this.sha = sha;
		this.output = output;
		this.status = Status.COMPLETED;
		if (output.errorCount > 0 || output.failureCount > 0) {
			this.conclusion = Conclusion.FAILURE;
		} else {
			this.conclusion = Conclusion.SUCCESS;
		}
	}

	/**
	 * Output of the check run.
	 */
	public static class CheckRunOutput {

		String title = "";
		String summary = "";
		String text = "";
		transient int testCount;
		transient int failureCount;
		transient int errorCount;

		List<CheckRunAnnotation> annotations;

		public Collection<CheckRunAnnotation> getAnnotations() {
			return annotations;
		}

		public void addCounts(int testCount, int failureCount, int errorCount) {
			this.testCount += testCount;
			this.failureCount += failureCount;
			this.errorCount += errorCount;
		}

		public void addAnnotation(CheckRunAnnotation annotation) {
			if (this.annotations == null) {
				this.annotations = new ArrayList<>();
			}
			this.annotations.add(annotation);
		}

		public void sortAnnotations() {
			if (this.annotations != null) {
				Collections.sort(annotations);
			}
		}

		public int getTestCount() {
			return testCount;
		}

		public int getFailureCount() {
			return failureCount;
		}

		public int getErrorCount() {
			return errorCount;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public void setSummary(String summary) {
			this.summary = summary;
		}

		public void setText(String text) {
			this.text = text;
		}
	}

	/**
	 * Annotation to the check-run that highlights specific test information. It is designed to be for files that are
	 * referenced in the particular commit in question.
	 */
	public static class CheckRunAnnotation implements Comparable<CheckRunAnnotation> {
		String path;
		@SerializedName("start_line")
		int startLine;
		@SerializedName("end_line")
		int endLine;
		@SerializedName("start_column")
		int startColumn;
		@SerializedName("end_column")
		int endColumn;
		@SerializedName("annotation_level")
		CheckLevel level;
		String title;
		String message;
		@SerializedName("raw_details")
		String details;

		public CheckRunAnnotation(String path, int startLine, int endLine, CheckLevel level, String title,
				String message, String details) {
			this.path = path;
			this.startLine = startLine;
			this.endLine = endLine;
			this.level = level;
			this.title = title;
			this.message = message;
			this.details = details;
		}

		public CheckLevel getLevel() {
			return level;
		}

		@Override
		public int compareTo(CheckRunAnnotation other) {
			// we want higher levels first
			return other.level.compareValues(level);
		}
	}

	/**
	 * Level of the specific check.
	 */
	public static enum CheckLevel {
		@SerializedName("notice")
		NOTICE(1),
		@SerializedName("warning")
		WARNING(2),
		@SerializedName("failure")
		FAILURE(3),
		// serialized as failure but recorded differently
		@SerializedName("failure")
		ERROR(4),
		// end
		;

		private final int value;

		private CheckLevel(int value) {
			this.value = value;
		}

		public static CheckLevel fromTestLevel(
				com.j256.testcheckpublisher.plugin.frameworks.FrameworkTestResults.TestFileResult.TestLevel testLevel) {
			switch (testLevel) {
				case FAILURE:
					return CheckLevel.FAILURE;
				case ERROR:
					return CheckLevel.ERROR;
				case NOTICE:
				default:
					return CheckLevel.NOTICE;
			}
		}

		/**
		 * Compare the values of the level.
		 */
		public int compareValues(CheckLevel other) {
			return value - other.value;
		}
	}

	/**
	 * Status of the tests. If they are done then the {@link Conclusion} should be set.
	 */
	public static enum Status {
		@SerializedName("queued")
		QUEUED,
		@SerializedName("in_progress")
		IN_PROGRESS,
		@SerializedName("completed")
		COMPLETED,
		// end
		;
	}

	/**
	 * How the test concluded if the status is {@link Status#COMPLETED}.
	 */
	public static enum Conclusion {
		@SerializedName("success")
		SUCCESS,
		@SerializedName("failure")
		FAILURE,
		@SerializedName("neutral")
		NEUTRAL,
		@SerializedName("cancelled")
		CANCELLED,
		@SerializedName("skipped")
		SKIPPED,
		@SerializedName("timed_out")
		TIMED_OUT,
		@SerializedName("action_required")
		ACTION_REQUIRED,
		// end
		;
	}
}
