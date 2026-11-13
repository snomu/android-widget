package io.github.snomu.termwidget.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyVault(private val context: Context) {
    private val keyAlias = "termwidget_finnhub_key"
    private val prefsKey = stringPreferencesKey("finnhub_api_key_sealed")

    suspend fun hasKey(): Boolean {
        val sealed = context.appDataStore.data.map { it[prefsKey] }.first()
        return !sealed.isNullOrBlank()
    }

    suspend fun save(plain: String) {
        val ciphertext = encrypt(plain.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        context.appDataStore.edit { prefs ->
            prefs[prefsKey] = encoded
        }
    }

    suspend fun load(): String? {
        val sealed = context.appDataStore.data.map { it[prefsKey] }.first() ?: return null
        return try {
            val bytes = Base64.decode(sealed, Base64.NO_WRAP)
            String(decrypt(bytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clear() {
        context.appDataStore.edit { prefs ->
            prefs.remove(prefsKey)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        return ByteBuffer.allocate(4 + iv.size + encrypted.size)
            .putInt(iv.size)
            .put(iv)
            .put(encrypted)
            .array()
    }

    private fun decrypt(payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(payload)
        val ivSize = buffer.int
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
