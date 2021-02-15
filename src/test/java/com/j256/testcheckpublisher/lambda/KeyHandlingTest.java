package com.j256.testcheckpublisher.lambda;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;

public class KeyHandlingTest {

	/**
	 * Reading in our fake key.
	 */
	public static PrivateKey readPrivateKey() throws IOException, GeneralSecurityException {
		try (InputStream input = KeyHandlingTest.class.getClassLoader().getResourceAsStream("fake_key.pem");) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			while (true) {
				int numRead = input.read(buf);
				if (numRead < 0) {
					break;
				}
				baos.write(buf, 0, numRead);
			}
			return KeyHandling.readKey(new String(baos.toByteArray()));
		}
	}
}
