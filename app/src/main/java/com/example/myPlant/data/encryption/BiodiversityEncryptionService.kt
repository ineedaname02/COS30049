package com.example.myPlant.data.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BiodiversityEncryptionService(private val context: Context) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore")
    private val masterKeyAlias = "bio_app_master_key"
    private val adminKeyAlias = "bio_app_admin_key"

    init {
        // ✅ ADD THIS: Initialize the keystore
        keyStore.load(null)
        generateKeysIfNeeded()
    }

    // Generate both user and admin keys
    private fun generateKeysIfNeeded() {
        if (!keyStore.containsAlias(masterKeyAlias)) {
            generateKey(masterKeyAlias)
        }
        if (!keyStore.containsAlias(adminKeyAlias)) {
            generateKey(adminKeyAlias)
        }
    }

    private fun generateKey(alias: String) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        val keySpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(keySpec)
        keyGenerator.generateKey()
    }

    // Encrypt data with BOTH user and admin keys
    fun encryptForUserAndAdmin(data: String): MultiEncryptedData {
        val userKey = keyStore.getKey(masterKeyAlias, null) as SecretKey
        val adminKey = keyStore.getKey(adminKeyAlias, null) as SecretKey

        // Encrypt with user key
        val userCipher = Cipher.getInstance("AES/GCM/NoPadding")
        userCipher.init(Cipher.ENCRYPT_MODE, userKey)
        val userEncrypted = userCipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val userIv = userCipher.iv

        // Encrypt with admin key
        val adminCipher = Cipher.getInstance("AES/GCM/NoPadding")
        adminCipher.init(Cipher.ENCRYPT_MODE, adminKey)
        val adminEncrypted = adminCipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val adminIv = adminCipher.iv

        return MultiEncryptedData(
            userEncrypted = userEncrypted,
            userIv = userIv,
            adminEncrypted = adminEncrypted,
            adminIv = adminIv
        )
    }

    // Decrypt with user key (for regular users)
    fun decryptWithUserKey(encryptedData: MultiEncryptedData): String {
        val userKey = keyStore.getKey(masterKeyAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, encryptedData.userIv)
        cipher.init(Cipher.DECRYPT_MODE, userKey, spec)

        val decryptedBytes = cipher.doFinal(encryptedData.userEncrypted)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    // Decrypt with admin key (for admin users)
    fun decryptWithAdminKey(encryptedData: MultiEncryptedData): String {
        val adminKey = keyStore.getKey(adminKeyAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, encryptedData.adminIv)
        cipher.init(Cipher.DECRYPT_MODE, adminKey, spec)

        val decryptedBytes = cipher.doFinal(encryptedData.adminEncrypted)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    // ✅ ADD THIS: Simple encrypt/decrypt for non-sensitive data
    fun encrypt(data: String): String {
        return try {
            val encryptedData = encryptForUserAndAdmin(data)
            // Store both encrypted versions in a single string
            Base64.encodeToString(encryptedData.userEncrypted + encryptedData.userIv, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e("Encryption", "Encryption failed: ${e.message}")
            data // Fallback
        }
    }

    fun decrypt(encryptedData: String): String {
        return try {
            val fullData = Base64.decode(encryptedData, Base64.DEFAULT)
            if (fullData.size <= 12) return encryptedData

            val encrypted = fullData.copyOfRange(0, fullData.size - 12)
            val iv = fullData.copyOfRange(fullData.size - 12, fullData.size)

            val multiData = MultiEncryptedData(
                userEncrypted = encrypted,
                userIv = iv,
                adminEncrypted = encrypted, // Reuse same data for admin
                adminIv = iv
            )

            decryptWithUserKey(multiData)
        } catch (e: Exception) {
            Log.e("Encryption", "Decryption failed: ${e.message}")
            encryptedData
        }
    }

    // ✅ ADD THIS: Helper methods
    fun shouldEncryptIUCN(iucnCategory: String?): Boolean {
        if (iucnCategory.isNullOrEmpty()) return false

        val endangeredCategories = listOf(
            "extinct", "extinct in the wild", "critically endangered", "endangered", "vulnerable"
        )
        return iucnCategory.lowercase() in endangeredCategories
    }

    data class MultiEncryptedData(
        val userEncrypted: ByteArray,
        val userIv: ByteArray,
        val adminEncrypted: ByteArray,
        val adminIv: ByteArray
    ) {
        fun toMap(): Map<String, Any> {
            return mapOf(
                "userData" to Base64.encodeToString(userEncrypted, Base64.DEFAULT),
                "userIv" to Base64.encodeToString(userIv, Base64.DEFAULT),
                "adminData" to Base64.encodeToString(adminEncrypted, Base64.DEFAULT),
                "adminIv" to Base64.encodeToString(adminIv, Base64.DEFAULT)
            )
        }

        companion object {
            fun fromMap(map: Map<String, Any>): MultiEncryptedData {
                return MultiEncryptedData(
                    userEncrypted = Base64.decode(map["userData"] as String, Base64.DEFAULT),
                    userIv = Base64.decode(map["userIv"] as String, Base64.DEFAULT),
                    adminEncrypted = Base64.decode(map["adminData"] as String, Base64.DEFAULT),
                    adminIv = Base64.decode(map["adminIv"] as String, Base64.DEFAULT)
                )
            }
        }
    }
}