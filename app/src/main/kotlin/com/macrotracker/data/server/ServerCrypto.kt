package com.macrotracker.data.server

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM wrapper backed by the Android Keystore, used for SSH passwords and
 * private keys.
 *
 * The key material itself never leaves the Keystore, so the ciphertext sitting
 * in SharedPrefs is useless on its own — an adb backup or a pulled prefs file
 * does not hand over anyone's server password. Nothing here requires user
 * authentication, because the monitor has to reconnect from a background
 * service while the phone is locked.
 */
object ServerCrypto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "dailydash_server_secrets"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    /** Returns Base64 of `iv || ciphertext`, or null when the Keystore is unusable. */
    fun encrypt(plaintext: String): String? {
        if (plaintext.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
        }.getOrNull()
    }

    fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return if (stored == "") "" else null
        return runCatching {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            if (blob.size <= IV_LENGTH) return@runCatching null
            val iv = blob.copyOfRange(0, IV_LENGTH)
            val body = blob.copyOfRange(IV_LENGTH, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
