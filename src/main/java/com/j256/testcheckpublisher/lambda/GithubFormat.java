package com.j256.testcheckpublisher.lambda;

/**
 * Format of the results that we post to github.
 */
public class GithubFormat {

	private final boolean noDetails;
	private final boolean noEmoji;
	private final boolean noPass;
	private final boolean alwaysAnnotate;
	private final boolean noAnnotate;
	private final boolean passDetails;

	public GithubFormat(boolean noDetails, boolean noEmoji, boolean noPass, boolean alwaysAnnotate, boolean noAnnotate,
			boolean passDetails) {
		this.noDetails = noDetails;
		this.noEmoji = noEmoji;
		this.noPass = noPass;
		this.alwaysAnnotate = alwaysAnnotate;
		this.noAnnotate = noAnnotate;
		this.passDetails = passDetails;
	}

	/**
	 * Write failures and errors into the details if they aren't in the commit.
	 */
	public boolean isNoDetails() {
		return noDetails;
	}

	public boolean isNoEmoji() {
		return noEmoji;
	}

	public boolean isNoPass() {
		return noPass;
	}

	public boolean isAlwaysAnnotate() {
		return alwaysAnnotate;
	}

	public boolean isNoAnnotate() {
		return noAnnotate;
	}

	public boolean isPassDetails() {
		return passDetails;
	}

	/**
	 * Find the format for our string returning default one if null.
	 */
	public static GithubFormat fromString(String str) {
		boolean noDetails = false;
		boolean noEmoji = false;
		boolean noPass = false;
		boolean alwaysAnnotate = false;
		boolean noAnnotate = false;
		boolean passDetails = false;
		if (!StringUtils.isEmpty(str)) {
			String[] tokens = StringUtils.split(str.toLowerCase(), ',');
			for (String token : tokens) {
				switch (token) {
					case "nodetails":
						noDetails = true;
						break;
					case "noannotate":
						noAnnotate = true;
						break;
					case "nonotice":
					case "nopass":
						noPass = true;
						break;
					case "alwaysannotate":
						alwaysAnnotate = true;
						break;
					case "noemoji":
						noEmoji = true;
						break;
					case "alldetails":
					case "passdetails":
						passDetails = true;
						break;
					default:
						// ignored
						break;
				}
			}
		}
		return new GithubFormat(noDetails, noEmoji, noPass, alwaysAnnotate, noAnnotate, passDetails);
	}
}
