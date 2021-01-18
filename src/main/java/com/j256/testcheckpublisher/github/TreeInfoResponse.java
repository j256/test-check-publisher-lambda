package com.j256.testcheckpublisher.github;

import com.google.gson.annotations.SerializedName;

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

	public static class TreeFile {

		private String path;
		private String type;
		private String sha;

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
