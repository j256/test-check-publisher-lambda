package com.j256.testcheckpublisher.lambda;

/**
 * Format of the results that we post to github.
 */
public class GithubFormat {

	private final boolean showDetails;
	private final boolean showEmoji;
	private final boolean showNotice;
	private final boolean gridOnly;

	public GithubFormat(boolean showDetails, boolean showEmoji, boolean showNotice, boolean gridOnly) {
		this.showDetails = showDetails;
		this.showEmoji = showEmoji;
		this.showNotice = showNotice;
		this.gridOnly = gridOnly;
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

	public boolean isGridOnly() {
		return gridOnly;
	}

	/**
	 * Find the format for our string returning default one if null.
	 */
	public static GithubFormat fromString(String str) {
		boolean showDetails = true;
		boolean showEmoji = true;
		boolean showNotice = true;
		boolean gridOnly = false;
		if (!StringUtils.isEmpty(str)) {
			String[] tokens = StringUtils.split(str, ',');
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
					case "grid":
						gridOnly = true;
						break;
					default:
						// ignored
						break;
				}
			}
		}
		return new GithubFormat(showDetails, showEmoji, showNotice, gridOnly);
	}
}
