package com.j256.testcheckpublisher.lambda.github;

/**
 * Response from the get commit command: https://docs.github.com/en/rest/reference/repos#get-a-commit
 * 
 * @author graywatson
 */
public class CommitInfoResponse {

	private String sha;
	private Commit commit;
	private ChangedFile[] files;

	public CommitInfoResponse() {
		// for gson
	}

	public CommitInfoResponse(String sha, Commit commit, ChangedFile[] files) {
		this.sha = sha;
		this.commit = commit;
		this.files = files;
	}

	public String getSha() {
		return sha;
	}

	public Commit getCommit() {
		return commit;
	}

	public ChangedFile[] getFiles() {
		return files;
	}

	public String getTreeSha() {
		if (commit == null || commit.tree == null) {
			return null;
		} else {
			return commit.tree.sha;
		}
	}

	public static class Commit {
		Tree tree;

		public Commit() {
			// for gson
		}

		public Commit(Tree tree) {
			this.tree = tree;
		}

		public Tree getTree() {
			return tree;
		}
	}

	public static class Tree {
		String sha;

		public Tree() {
			// for gson
		}

		public Tree(String sha) {
			this.sha = sha;
		}

		public String getSha() {
			return sha;
		}
	}

	public static class ChangedFile {
		String filename;
		// added, removed, modified, renamed
		String status;

		public ChangedFile() {
			// for gson
		}

		public ChangedFile(String filename, String status) {
			this.filename = filename;
			this.status = status;
		}

		public String getFilename() {
			return filename;
		}

		public String getStatus() {
			return status;
		}

		@Override
		public String toString() {
			return filename;
		}
	}
}
