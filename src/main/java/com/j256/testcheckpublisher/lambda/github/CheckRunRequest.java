package com.j256.testcheckpublisher.lambda.github;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult;

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

	public Conclusion getConclusion() {
		return conclusion;
	}

	public CheckRunOutput getOutput() {
		return output;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = prime + ((conclusion == null) ? 0 : conclusion.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((output == null) ? 0 : output.hashCode());
		result = prime * result + ((sha == null) ? 0 : sha.hashCode());
		result = prime * result + ((status == null) ? 0 : status.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		CheckRunRequest other = (CheckRunRequest) obj;
		if (conclusion != other.conclusion) {
			return false;
		} else if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (output == null) {
			if (other.output != null) {
				return false;
			}
		} else if (!output.equals(other.output)) {
			return false;
		}
		if (sha == null) {
			if (other.sha != null) {
				return false;
			}
		} else if (!sha.equals(other.sha)) {
			return false;
		}
		return (status == other.status);
	}

	/**
	 * Output of the check run.
	 */
	public static class CheckRunOutput {

		String title = "";
		// supports markdown
		String summary = "";
		// supports markdown
		String text = "";
		List<CheckRunAnnotation> annotations;
		transient int testCount;
		transient int failureCount;
		transient int errorCount;

		public CheckRunOutput() {
			// for gson
		}

		public CheckRunOutput(String title, String summary, String text, List<CheckRunAnnotation> annotations,
				int testCount, int failureCount, int errorCount) {
			this.title = title;
			this.summary = summary;
			this.text = text;
			this.annotations = annotations;
			this.testCount = testCount;
			this.failureCount = failureCount;
			this.errorCount = errorCount;
		}

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

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = prime + ((annotations == null) ? 0 : annotations.hashCode());
			result = prime * result + errorCount;
			result = prime * result + failureCount;
			result = prime * result + ((summary == null) ? 0 : summary.hashCode());
			result = prime * result + testCount;
			result = prime * result + ((text == null) ? 0 : text.hashCode());
			result = prime * result + ((title == null) ? 0 : title.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			CheckRunOutput other = (CheckRunOutput) obj;
			if (annotations == null) {
				if (other.annotations != null) {
					return false;
				}
			} else if (!annotations.equals(other.annotations)) {
				return false;
			}
			if (errorCount != other.errorCount) {
				return false;
			}
			if (failureCount != other.failureCount) {
				return false;
			}
			if (testCount != other.testCount) {
				return false;
			}
			if (summary == null) {
				if (other.summary != null) {
					return false;
				}
			} else if (!summary.equals(other.summary)) {
				return false;
			}
			if (text == null) {
				if (other.text != null) {
					return false;
				}
			} else if (!text.equals(other.text)) {
				return false;
			}
			if (title == null) {
				if (other.title != null) {
					return false;
				}
			} else if (!title.equals(other.title)) {
				return false;
			}
			return true;
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

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = prime + ((details == null) ? 0 : details.hashCode());
			result = prime * result + endColumn;
			result = prime * result + endLine;
			result = prime * result + ((level == null) ? 0 : level.hashCode());
			result = prime * result + ((message == null) ? 0 : message.hashCode());
			result = prime * result + ((path == null) ? 0 : path.hashCode());
			result = prime * result + startColumn;
			result = prime * result + startLine;
			result = prime * result + ((title == null) ? 0 : title.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			CheckRunAnnotation other = (CheckRunAnnotation) obj;
			if (details == null) {
				if (other.details != null) {
					return false;
				}
			} else if (!details.equals(other.details)) {
				return false;
			}
			if (endColumn != other.endColumn) {
				return false;
			}
			if (endLine != other.endLine) {
				return false;
			}
			if (level != other.level) {
				return false;
			}
			if (message == null) {
				if (other.message != null) {
					return false;
				}
			} else if (!message.equals(other.message)) {
				return false;
			}
			if (path == null) {
				if (other.path != null) {
					return false;
				}
			} else if (!path.equals(other.path)) {
				return false;
			}
			if (startColumn != other.startColumn) {
				return false;
			}
			if (startLine != other.startLine) {
				return false;
			}
			if (title == null) {
				if (other.title != null) {
					return false;
				}
			} else if (!title.equals(other.title)) {
				return false;
			}
			return true;
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

		public static CheckLevel fromTestLevel(TestFileResult.TestLevel testLevel) {
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
