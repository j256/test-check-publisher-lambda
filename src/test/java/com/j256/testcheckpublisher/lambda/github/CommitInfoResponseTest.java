package com.j256.testcheckpublisher.lambda.github;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse.ChangedFile;
import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse.Commit;
import com.j256.testcheckpublisher.lambda.github.CommitInfoResponse.Tree;

public class CommitInfoResponseTest {

	@Test
	public void testStuff() {
		String treeSha = "fewopjfpwejfwe";
		Tree tree = new Tree(treeSha);
		assertEquals(treeSha, tree.getSha());
		new Tree();

		Commit commit = new Commit(tree);
		assertEquals(tree, commit.getTree());

		String filename = "fepowjfpjfew";
		String status = "epojfpjewfewf";
		ChangedFile changedFile = new ChangedFile(filename, status);
		assertEquals(filename, changedFile.getFilename());
		assertEquals(status, changedFile.getStatus());
		assertEquals(filename, changedFile.toString());

		String sha = "fejwjfwejf";
		ChangedFile[] files = new ChangedFile[] { changedFile, new ChangedFile() };
		CommitInfoResponse response = new CommitInfoResponse(sha, commit, files);
		assertEquals(sha, response.getSha());
		assertEquals(commit, response.getCommit());
		assertArrayEquals(files, response.getFiles());
		assertEquals(treeSha, response.getTreeSha());

		assertNull(new CommitInfoResponse().getTreeSha());

		response = new CommitInfoResponse(sha, new Commit(), files);
		assertNull(response.getTreeSha());
	}
}
