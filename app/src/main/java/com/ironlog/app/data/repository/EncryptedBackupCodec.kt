package com.ironlog.app.data.repository

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/** Compatible V1 encryption, with explicit confirmation and bounded untrusted KDF work. */
object EncryptedBackupCodec {
    private const val SCHEMA = "IRONLOG_ENCRYPTED_EXPORT_V1"
    private const val KDF = "PBKDF2WithHmacSHA256"

    fun encrypt(plainText: String, passphrase: String, confirmation: String): String {
        require(passphrase.length >= 8 && passphrase == confirmation) { "Enter matching passphrases of at least 8 characters." }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val encrypted = crypt(Cipher.ENCRYPT_MODE, plainText.toByteArray(Charsets.UTF_8), passphrase, salt, iv, 120000)
        return JSONObject().put("schema", SCHEMA).put("kdf", KDF).put("iterations", 120000)
            .put("saltB64", Base64.getEncoder().encodeToString(salt))
            .put("ivB64", Base64.getEncoder().encodeToString(iv))
            .put("ciphertextB64", Base64.getEncoder().encodeToString(encrypted))
            .put("sha256", MessageDigest.getInstance("SHA-256").digest(plainText.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
            .put("exportedAt", System.currentTimeMillis()).toString(2)
    }

    fun decrypt(raw: String, passphrase: String): String {
        val payload = JSONObject(raw)
        require(payload.optString("schema") == SCHEMA && payload.optString("kdf", KDF) == KDF) { "Unsupported encrypted backup." }
        val rawIterations = payload.opt("iterations") ?: 120000
        require(rawIterations is Number && rawIterations.toDouble() % 1.0 == 0.0 && rawIterations.toDouble() in 10000.0..1000000.0) { "Unsupported key derivation work factor." }
        fun bytes(key: String) = Base64.getDecoder().decode(payload.getString(key).filterNot(Char::isWhitespace))
        val salt = bytes("saltB64")
        val iv = bytes("ivB64")
        val ciphertext = bytes("ciphertextB64")
        require(salt.size == 16 && iv.size == 12 && ciphertext.size >= 16) { "Corrupt encrypted backup." }
        return String(crypt(Cipher.DECRYPT_MODE, ciphertext, passphrase, salt, iv, rawIterations.toInt()), Charsets.UTF_8)
    }

    private fun crypt(mode: Int, data: ByteArray, passphrase: String, salt: ByteArray, iv: ByteArray, iterations: Int): ByteArray {
        val chars = passphrase.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        chars.fill('\u0000')
        val key = try { SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded } finally { spec.clearPassword() }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            return cipher.doFinal(data)
        } finally { key.fill(0) }
    }
}
