package com.j256.testcheckpublisher.lambda.github;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.j256.testcheckpublisher.lambda.github.TreeInfoResponse.TreeFile;

public class TreeInfoResponseTest {

	@Test
	public void testStuff() {
		String path = "pewojfpwejfpew";
		String type = "pewo30-84483jfpwejfpew";
		String treeSha = "fewjpfewjppewojfpwejfpew";
		TreeFile treeFile = new TreeFile(path, type, treeSha);
		assertEquals(path, treeFile.getPath());
		assertEquals(type, treeFile.getType());
		assertEquals(treeSha, treeFile.getSha());
		assertEquals(path, treeFile.toString());

		TreeFile[] files = new TreeFile[] { treeFile, new TreeFile() };

		String sha = "fwejopfewpj";
		TreeInfoResponse response = new TreeInfoResponse(sha, files);
		assertEquals(sha, response.getSha());
		assertArrayEquals(files, response.getTreeFiles());

		new TreeInfoResponse();
	}
}
