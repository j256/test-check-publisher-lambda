package com.j256.testcheckpublisher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Handle our private key file.
 */
public class KeyHandling {

	private static final String PKCS_1_PEM_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
	private static final String PKCS_1_PEM_FOOTER = "-----END RSA PRIVATE KEY-----";
	private static final String PKCS_8_PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
	private static final String PKCS_8_PEM_FOOTER = "-----END PRIVATE KEY-----";

	public static PrivateKey loadKey(String keyFilePath) throws GeneralSecurityException, IOException {
		byte[] keyDataBytes = Files.readAllBytes(Paths.get(keyFilePath));
		String keyDataString = new String(keyDataBytes, StandardCharsets.UTF_8);
		return readKey(keyDataString);
	}

	public static PrivateKey readKey(String keyDataString) throws GeneralSecurityException, IOException {

		if (keyDataString.contains(PKCS_1_PEM_HEADER)) {
			// OpenSSL / PKCS#1 Base64 PEM encoded file
			keyDataString = keyDataString.replace(PKCS_1_PEM_HEADER, "");
			keyDataString = keyDataString.replace(PKCS_1_PEM_FOOTER, "");
			keyDataString = keyDataString.replace(System.lineSeparator(), "");
			return readPkcs1PrivateKey(Base64.getDecoder().decode(keyDataString));
		}

		if (keyDataString.contains(PKCS_8_PEM_HEADER)) {
			// PKCS#8 Base64 PEM encoded file
			keyDataString = keyDataString.replace(PKCS_8_PEM_HEADER, "");
			keyDataString = keyDataString.replace(PKCS_8_PEM_FOOTER, "");
			keyDataString = keyDataString.replace(System.lineSeparator(), "");
			return readPkcs8PrivateKey(Base64.getDecoder().decode(keyDataString));
		}

		// We assume it's a PKCS#8 DER encoded binary file
		return readPkcs8PrivateKey(keyDataString.getBytes());
	}

	private static PrivateKey readPkcs8PrivateKey(byte[] pkcs8Bytes) throws GeneralSecurityException {
		KeyFactory keyFactory = KeyFactory.getInstance("RSA", "SunRsaSign");
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8Bytes);
		try {
			return keyFactory.generatePrivate(keySpec);
		} catch (InvalidKeySpecException e) {
			throw new IllegalArgumentException("Unexpected key format!", e);
		}
	}

	private static PrivateKey readPkcs1PrivateKey(byte[] pkcs1Bytes) throws GeneralSecurityException, IOException {
		// We can't use Java internal APIs to parse ASN.1 structures, so we build a PKCS#8 key Java can understand
		int pkcs1Length = pkcs1Bytes.length;
		// initial data after the initial total length information
		byte[] initialHeader = new byte[] {
				// Integer (0)
				0x2, 0x1, 0x0,
				// Sequence: 1.2.840.113549.1.1.1, NULL
				0x30, 0xD, 0x6, 0x9, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0xD, 0x1, 0x1, 0x1, 0x5, 0x0,
				// Octet string + // length
				0x4, (byte) 0x82, (byte) ((pkcs1Length >> 8) & 0xff), (byte) (pkcs1Length & 0xff),
				// end
		};
		int totalLength = initialHeader.length + pkcs1Length;
		byte[] pkcs8Header =
				// sequence + total length
				new byte[] { 0x30, (byte) 0x82, (byte) ((totalLength >> 8) & 0xff), (byte) (totalLength & 0xff) };
		ByteArrayOutputStream baos =
				new ByteArrayOutputStream(pkcs8Header.length + initialHeader.length + pkcs1Bytes.length);
		baos.write(pkcs8Header);
		baos.write(initialHeader);
		baos.write(pkcs1Bytes);
		return readPkcs8PrivateKey(baos.toByteArray());
	}
}
