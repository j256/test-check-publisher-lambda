package com.j256.testcheckpublisher.lambda;

import static org.junit.Assert.*;

import org.junit.Test;

public class FileInfoTest {

	@Test
	public void testStuff() {
		String path = "qpfjewpfjeqw";
		String sha = "fepwfpewjfwe";
		boolean inCommit = true;
		FileInfo fileInfo = new FileInfo(path, sha, inCommit);
		assertEquals(path, fileInfo.getPath());
		assertEquals(sha, fileInfo.getSha());
		assertEquals(inCommit, fileInfo.isInCommit());
		assertEquals(path, fileInfo.toString());
	}

	@Test
	public void testPathToName() {
		String path = "dwqdqqwdqw";
		FileInfo fileInfo = new FileInfo(path, "sha", true);
		assertEquals(path, fileInfo.getPath());
		assertEquals(path, fileInfo.getName());

		String name = "foo";
		path = "/dir/to/" + name;
		fileInfo = new FileInfo(path, "sha", true);
		assertEquals(path, fileInfo.getPath());
		assertEquals(name, fileInfo.getName());

		path = "/";
		fileInfo = new FileInfo(path, "sha", true);
		assertEquals(path, fileInfo.getPath());
		assertEquals(path, fileInfo.getName());

		path = "\\";
		fileInfo = new FileInfo(path, "sha", true);
		assertEquals(path, fileInfo.getPath());
		assertEquals(path, fileInfo.getName());

		path = "\\hello\\" + name;
		fileInfo = new FileInfo(path, "sha", true);
		assertEquals(path, fileInfo.getPath());
		assertEquals(name, fileInfo.getName());
	}
}
