package com.contax.app.data.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup & PIN crypto:
 * - PASSWORD mode: PBKDF2WithHmacSHA256 → AES-256-GCM, envelope: CNTX1 + salt(16) + iv(12) + ciphertext
 * - DEVICE_KEY mode: Android Keystore AES key, envelope: CNTX1K + iv(12) + ciphertext
 * - PIN: PBKDF2 hash "v1:saltB64:hashB64"
 *
 * برای هش PIN از java.util.Base64 استفاده می‌کنیم (نه android.util.Base64)
 * تا مسیر hash/verify روی JVM هم قابل تست باشد؛ minSdk 26 کاملاً پشتیبانی می‌کند.
 */
object BackupCrypto {

    // visible to repositories/viewmodels that sniff the file header
    const val MAGIC = "CNTX1"
    private const val KEYSTORE_ALIAS = "contax_master_key"
    private const val PBKDF2_ITERATIONS = 60_000

    // ── password-based ──

    fun encryptPassword(plain: String, password: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = SecretKeySpec(pbkdf2(password, salt), "AES")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return MAGIC.toByteArray(Charsets.UTF_8) + salt + iv + ct
    }

    fun decryptPassword(data: ByteArray, password: String): String? {
        return try {
            if (data.size < 33 || String(data, 0, MAGIC.length, Charsets.UTF_8) != MAGIC) {
                null
            } else {
                val salt = data.copyOfRange(MAGIC.length, MAGIC.length + 16)
                val iv = data.copyOfRange(MAGIC.length + 16, MAGIC.length + 28)
                val ct = data.copyOfRange(MAGIC.length + 28, data.size)
                val key = SecretKeySpec(pbkdf2(password, salt), "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                String(cipher.doFinal(ct), Charsets.UTF_8)
            }
        } catch (t: Throwable) {
            null
        }
    }

    // ── device-key (Keystore) ──

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encryptDeviceKey(plain: String): ByteArray = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val header = (MAGIC + "K").toByteArray(Charsets.UTF_8)
        header + iv + ct
    } catch (t: Throwable) {
        throw IllegalStateException("keystore_encrypt_failed", t)
    }

    fun decryptDeviceKey(data: ByteArray): String? {
        return try {
            val headerLen = MAGIC.length + 1
            if (data.size < headerLen + 12 || String(data, 0, headerLen, Charsets.UTF_8) != MAGIC + "K") {
                null
            } else {
                val iv = data.copyOfRange(headerLen, headerLen + 12)
                val ct = data.copyOfRange(headerLen + 12, data.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
                String(cipher.doFinal(ct), Charsets.UTF_8)
            }
        } catch (t: Throwable) {
            null
        }
    }

    // ── PIN ──

    fun hashPin(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        return "v1:" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash)
    }

    fun verifyPin(pin: String, stored: String): Boolean = try {
        val parts = stored.split(":")
        if (parts.size != 3 || parts[0] != "v1") false
        else {
            val salt = Base64.getMimeDecoder().decode(parts[1])
            val expected = Base64.getMimeDecoder().decode(parts[2])
            MessageDigest.isEqual(pbkdf2(pin, salt), expected)
        }
    } catch (t: Throwable) {
        false
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}
