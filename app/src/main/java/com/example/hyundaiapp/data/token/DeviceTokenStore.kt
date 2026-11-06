package com.example.hyundaiapp.data.token

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 기기 토큰 저장소. 테스트에서는 메모리 구현으로 바꿔 끼운다 */
interface TokenStorage {
    fun load(): String?
    fun save(token: String)
    fun clear()
}

/**
 * 기기 토큰을 Android Keystore의 AES 키로 암호화해 SharedPreferences에 저장한다.
 * 키는 Keystore 밖으로 나오지 않는다. 복호화에 실패하면 토큰을 지워 다시 발급받게 한다.
 */
class DeviceTokenStore(context: Context) : TokenStorage {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): String? {
        val stored = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            decrypt(stored)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    override fun save(token: String) {
        prefs.edit().putString(KEY_TOKEN, encrypt(token)).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "${cipher.iv.toBase64()}:${encrypted.toBase64()}"
    }

    private fun decrypt(stored: String): String {
        val (iv, encrypted) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private companion object {
        const val PREFS_NAME = "device_token"
        const val KEY_TOKEN = "token"
        const val KEY_ALIAS = "device_token_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
