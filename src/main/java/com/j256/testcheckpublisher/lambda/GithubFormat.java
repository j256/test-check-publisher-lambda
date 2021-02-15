package com.j256.testcheckpublisher.lambda;

/**
 * Format of the results that we post to github.
 */
public class GithubFormat {

	private final boolean showDetails;
	private final boolean showEmoji;
	private final boolean showNotice;
	private final boolean alwaysAnnotate;
	private final boolean noAnnotate;
	private final boolean allDetails;

	public GithubFormat(boolean showDetails, boolean showEmoji, boolean showNotice, boolean alwaysAnnotate,
			boolean noAnnotate, boolean allDetails) {
		this.showDetails = showDetails;
		this.showEmoji = showEmoji;
		this.showNotice = showNotice;
		this.alwaysAnnotate = alwaysAnnotate;
		this.noAnnotate = noAnnotate;
		this.allDetails = allDetails;
	}

	/**
	 * Write failures and errors into the details if they aren't in the commit.
	 */
	public boolean isShowDetails() {
		return showDetails;
	}

	public boolean isShowEmoji() {
		return showEmoji;
	}

	public boolean isShowNotice() {
		return showNotice;
	}

	public boolean isAlwaysAnnotate() {
		return alwaysAnnotate;
	}

	public boolean isNoAnnotate() {
		return noAnnotate;
	}

	public boolean isAllDetails() {
		return allDetails;
	}

	/**
	 * Find the format for our string returning default one if null.
	 */
	public static GithubFormat fromString(String str) {
		boolean showDetails = true;
		boolean showEmoji = true;
		boolean showNotice = true;
		boolean alwaysAnnotate = false;
		boolean noAnnotate = false;
		boolean allDetails = false;
		if (!StringUtils.isEmpty(str)) {
			String[] tokens = StringUtils.split(str.toLowerCase(), ',');
			for (String token : tokens) {
				switch (token) {
					case "nodetails":
						showDetails = false;
						break;
					case "noemoji":
						showEmoji = false;
						break;
					case "nonotice":
						showNotice = false;
						break;
					case "alwaysannotate":
						alwaysAnnotate = true;
						break;
					case "noannotate":
						noAnnotate = true;
						break;
					case "alldetails":
						allDetails = true;
						break;
					default:
						// ignored
						break;
				}
			}
		}
		return new GithubFormat(showDetails, showEmoji, showNotice, alwaysAnnotate, noAnnotate, allDetails);
	}
}
