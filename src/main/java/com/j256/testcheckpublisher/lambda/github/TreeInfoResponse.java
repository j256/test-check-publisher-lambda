package com.j256.testcheckpublisher.lambda.github;

import com.google.gson.annotations.SerializedName;

/**
 * Get information about the file tree: https://docs.github.com/en/rest/reference/git#get-a-tree
 * 
 * @author graywatson
 */
public class TreeInfoResponse {

	private String sha;
	@SerializedName("tree")
	private TreeFile[] treeFiles;

	public String getSha() {
		return sha;
	}

	public TreeFile[] getTreeFiles() {
		return treeFiles;
	}

	/**
	 * Information about a file in the tree. 
	 */
	public static class TreeFile {

		private String path;
		private String type;
		private String sha;

		public TreeFile() {
			// for gson
		}

		public TreeFile(String path, String type, String sha) {
			this.path = path;
			this.type = type;
			this.sha = sha;
		}

		public String getPath() {
			return path;
		}

		public String getType() {
			return type;
		}

		public String getSha() {
			return sha;
		}

		@Override
		public String toString() {
			return path;
		}
	}
}
