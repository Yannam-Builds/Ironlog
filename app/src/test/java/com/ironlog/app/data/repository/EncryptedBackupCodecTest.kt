package com.ironlog.app.data.repository

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class EncryptedBackupCodecTest {
    @Test fun `exact confirmed passphrase round trips and hint does not unlock`() {
        val secret = "  synthetic private phrase  "
        val encrypted = EncryptedBackupCodec.encrypt("synthetic payload", secret, secret)
        assertEquals("synthetic payload", EncryptedBackupCodec.decrypt(encrypted, secret))
        assertTrue(runCatching { EncryptedBackupCodec.decrypt(encrypted, "sy****************") }.isFailure)
        assertTrue(runCatching { EncryptedBackupCodec.decrypt(encrypted, secret.trim()) }.isFailure)
    }

    @Test fun `confirmation and minimum length are required`() {
        assertTrue(runCatching { EncryptedBackupCodec.encrypt("payload", "password A", "password B") }.isFailure)
        assertTrue(runCatching { EncryptedBackupCodec.encrypt("payload", "short", "short") }.isFailure)
    }

    @Test fun `tampered ciphertext or excessive key derivation work is rejected`() {
        val secret = "test password"
        val encrypted = JSONObject(EncryptedBackupCodec.encrypt("payload", secret, secret))
        encrypted.put("iterations", Int.MAX_VALUE)
        assertTrue(runCatching { EncryptedBackupCodec.decrypt(encrypted.toString(), secret) }.isFailure)
        encrypted.put("iterations", 120000)
        encrypted.put("ciphertextB64", "AAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        assertTrue(runCatching { EncryptedBackupCodec.decrypt(encrypted.toString(), secret) }.isFailure)
    }
}
