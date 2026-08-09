package com.histudio.web.netbook.data;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Encrypts sensitive PostgreSQL values with the deployment-supplied household key. */
@Component
public final class AtRestCrypto {

	private static final int NONCE_BYTES = 12;
	private static final int GCM_TAG_BITS = 128;

	private final SecureRandom secureRandom = new SecureRandom();
	private final SecretKey key;

	public AtRestCrypto(@Value("${netbook.security.master-key:}") String encodedKey) {
		if (encodedKey == null || encodedKey.isBlank()) {
			throw new IllegalStateException("NETBOOK_MASTER_KEY must contain a base64-encoded 32-byte AES key.");
		}
		byte[] keyBytes;
		try {
			keyBytes = Base64.getDecoder().decode(encodedKey.strip());
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("NETBOOK_MASTER_KEY must be valid base64.", exception);
		}
		if (keyBytes.length != 32) {
			throw new IllegalStateException("NETBOOK_MASTER_KEY must decode to exactly 32 bytes.");
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	public Sealed encrypt(String value) {
		try {
			byte[] nonce = new byte[NONCE_BYTES];
			secureRandom.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
			return new Sealed(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), nonce);
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to encrypt NetBook data.", exception);
		}
	}

	public String decrypt(byte[] ciphertext, byte[] nonce) {
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to decrypt NetBook data. Check NETBOOK_MASTER_KEY.", exception);
		}
	}

	/** Stores a nonce and ciphertext together where the schema provides one BYTEA column. */
	public byte[] encryptCombined(String value) {
		Sealed sealed = encrypt(value);
		return ByteBuffer.allocate(NONCE_BYTES + sealed.ciphertext().length)
			.put(sealed.nonce())
			.put(sealed.ciphertext())
			.array();
	}

	public String decryptCombined(byte[] combined) {
		if (combined == null || combined.length <= NONCE_BYTES) {
			throw new IllegalStateException("Encrypted NetBook value is malformed.");
		}
		ByteBuffer buffer = ByteBuffer.wrap(combined);
		byte[] nonce = new byte[NONCE_BYTES];
		buffer.get(nonce);
		byte[] ciphertext = new byte[buffer.remaining()];
		buffer.get(ciphertext);
		return decrypt(ciphertext, nonce);
	}

	public byte[] digest(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (Exception exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	public String digestHex(String value) {
		byte[] digest = digest(value);
		StringBuilder hex = new StringBuilder(digest.length * 2);
		for (byte item : digest) hex.append(String.format("%02x", item));
		return hex.toString();
	}

	public record Sealed(byte[] ciphertext, byte[] nonce) {}
}
