package com.example.myPlant.data.encryption

import android.util.Base64
import android.util.Log
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionUtils {
    companion object {
        private const val TAG = "EncryptionUtils"
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val TAG_LENGTH = 16
        private const val IV_LENGTH = 12

        private var encryptionKey: String? = null

        suspend fun getEncryptionKey(): String {
            return encryptionKey ?: fetchEncryptionKeyFromCloudFunction()
        }

        private suspend fun fetchEncryptionKeyFromCloudFunction(): String {
            try {
                val functions = Firebase.functions("asia-southeast1")
                val result = functions
                    .getHttpsCallable("getDataKey")
                    .call()
                    .await()

                val data = result.data as? Map<*, *>
                val key = data?.get("key") as? String
                    ?: throw Exception("No key returned from cloud function")

                // Cache the key
                encryptionKey = key
                Log.d(TAG, "✅ Successfully fetched encryption key")
                return key

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to fetch encryption key: ${e.message}", e)
                when (e) {
                    is FirebaseFunctionsException -> {
                        when (e.code) {
                            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                                throw Exception("Admin access required. Please ensure you have admin privileges.")
                            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                                throw Exception("Please sign in first")
                            else -> throw Exception("Failed to get encryption key: ${e.message}")
                        }
                    }
                    else -> throw Exception("Failed to get encryption key: ${e.message}")
                }
            }
        }

        fun encrypt(data: String, key: String): String {
            return try {
                if (data.isEmpty()) return ""

                val keyBytes = key.toByteArray(Charsets.UTF_8)
                val keySpec = SecretKeySpec(keyBytes, "AES")
                val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

                val cipher = Cipher.getInstance(ALGORITHM)
                val gcmSpec = GCMParameterSpec(TAG_LENGTH * 8, iv)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

                val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
                val combined = iv + encrypted

                Base64.encodeToString(combined, Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Encryption failed: ${e.message}", e)
                throw RuntimeException("Encryption failed: ${e.message}")
            }
        }

        fun decrypt(encryptedData: String, key: String): String {
            return try {
                if (encryptedData.isEmpty()) return ""

                val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
                if (combined.size < IV_LENGTH) {
                    throw IllegalArgumentException("Invalid encrypted data")
                }

                val iv = combined.copyOfRange(0, IV_LENGTH)
                val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)

                val keyBytes = key.toByteArray(Charsets.UTF_8)
                val keySpec = SecretKeySpec(keyBytes, "AES")
                val cipher = Cipher.getInstance(ALGORITHM)
                val gcmSpec = GCMParameterSpec(TAG_LENGTH * 8, iv)

                cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
                val decrypted = cipher.doFinal(encrypted)

                String(decrypted, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Decryption failed: ${e.message}", e)
                throw RuntimeException("Decryption failed: ${e.message}")
            }
        }

        fun clearKeyCache() {
            encryptionKey = null
            Log.d(TAG, "🔑 Cleared encryption key cache")
        }

        fun isKeyAvailable(): Boolean {
            return encryptionKey != null
        }
    }
}