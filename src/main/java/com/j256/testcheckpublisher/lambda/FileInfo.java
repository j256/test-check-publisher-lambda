package com.j256.testcheckpublisher.lambda;

import java.io.File;

/**
 * Information about a particular file.
 * 
 * @author graywatson
 */
public class FileInfo {

	private final String path;
	private final String name;
	private final String sha;
	private final boolean inCommit;

	public FileInfo(String path, String sha, boolean inCommit) {
		this.path = path;
		// extract our file-name
		int index = path.lastIndexOf(File.separatorChar);
		if (index < path.length() - 1) {
			this.name = path.substring(index + 1);
		} else {
			this.name = path;
		}
		this.sha = sha;
		this.inCommit = inCommit;
	}

	public String getPath() {
		return path;
	}

	public String getName() {
		return name;
	}

	public String getSha() {
		return sha;
	}

	public boolean isInCommit() {
		return inCommit;
	}

	@Override
	public String toString() {
		return path;
	}
}
