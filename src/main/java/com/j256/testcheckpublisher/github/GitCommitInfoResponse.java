package com.j256.testcheckpublisher.github;

public class GitCommitInfoResponse {

	private String sha;
	private Tree tree;
	private ChangedFile[] files;

	public String getSha() {
		return sha;
	}

	public Tree getTree() {
		return tree;
	}

	public ChangedFile[] getFiles() {
		return files;
	}

	public static class Tree {
		String sha;

		public String getSha() {
			return sha;
		}
	}

	public static class ChangedFile {
		String filename;
		// added, removed, modified, renamed
		String status;
	}
}
