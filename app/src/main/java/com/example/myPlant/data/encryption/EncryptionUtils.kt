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
import com.google.firebase.auth.FirebaseAuth


class EncryptionUtils {
    companion object {

        private val auth: FirebaseAuth = FirebaseAuth.getInstance()

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

                val currentUser = auth.currentUser
                if (currentUser == null) {
                    throw Exception("Please sign in first - no authenticated user")
                }

                Log.d(TAG, "🔑 User authenticated: ${currentUser.uid}")


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
                Log.d("EncryptionUtils", "🔓 Attempting decryption...")
                Log.d("EncryptionUtils", "   Input length: ${encryptedData.length}")
                Log.d("EncryptionUtils", "   Key length: ${key.length}")

                if (encryptedData.isEmpty()) {
                    Log.d("EncryptionUtils", "⚠️ Empty encrypted data, returning empty string")
                    return ""
                }

                val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
                Log.d("EncryptionUtils", "   Base64 decoded length: ${combined.size}")

                if (combined.size < IV_LENGTH) {
                    Log.e("EncryptionUtils", "❌ Invalid encrypted data: too short")
                    throw IllegalArgumentException("Invalid encrypted data")
                }

                val iv = combined.copyOfRange(0, IV_LENGTH)
                val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)
                Log.d("EncryptionUtils", "   IV length: ${iv.size}, Encrypted length: ${encrypted.size}")

                val keyBytes = key.toByteArray(Charsets.UTF_8)
                val keySpec = SecretKeySpec(keyBytes, "AES")
                val cipher = Cipher.getInstance(ALGORITHM)
                val gcmSpec = GCMParameterSpec(TAG_LENGTH * 8, iv)

                cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
                val decrypted = cipher.doFinal(encrypted)

                val result = String(decrypted, Charsets.UTF_8)
                Log.d("EncryptionUtils", "✅ Decryption successful: '$result'")
                result

            } catch (e: Exception) {
                Log.e("EncryptionUtils", "❌ Decryption failed: ${e.message}", e)
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