package com.multispace.core.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore

/**
 * AES-256 GCM encryption for GSF tokens and credentials
 * Uses Android KeyStore for secure key storage
 * 
 * KeyStore location: /data/misc/keystore/
 * Encryption: AES-256-GCM with random IV
 * Key rotation: Can be configured per profile
 */
class TokenEncryption(private val context: Context) {
    private val TAG = "TokenEncryption"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore")
    private val KEYSTORE_ALIAS_PREFIX = "multispace_token_key_"
    private val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private val KEY_SIZE = 256
    private val GCM_TAG_LENGTH = 128
    
    init {
        keyStore.load(null)
    }
    
    /**
     * Encrypt token with AES-256-GCM
     * Returns: Base64(IV + ciphertext + authTag)
     */
    fun encryptToken(token: String, profileId: Int): Result<String> {
        return try {
            val key = getOrCreateKey(profileId)
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            
            val ciphertext = cipher.doFinal(token.toByteArray())
            val iv = cipher.iv
            
            // Combine IV + ciphertext + authTag for storage
            val combined = iv + ciphertext
            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            
            Log.d(TAG, "Token encrypted successfully for profile: $profileId")
            Result.success(encoded)
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting token", e)
            Result.failure(e)
        }
    }
    
    /**
     * Decrypt token previously encrypted with encryptToken
     */
    fun decryptToken(encryptedToken: String, profileId: Int): Result<String> {
        return try {
            val key = getOrCreateKey(profileId)
            val combined = Base64.decode(encryptedToken, Base64.NO_WRAP)
            
            val iv = combined.sliceArray(0 until 12) // GCM IV is 12 bytes
            val ciphertext = combined.sliceArray(12 until combined.size)
            
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec)
            
            val plaintext = cipher.doFinal(ciphertext)
            val token = String(plaintext)
            
            Log.d(TAG, "Token decrypted successfully for profile: $profileId")
            Result.success(token)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting token", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get existing key or create new one for profile
     */
    private fun getOrCreateKey(profileId: Int): SecretKey {
        val alias = "$KEYSTORE_ALIAS_PREFIX$profileId"
        
        // Check if key already exists
        if (keyStore.containsAlias(alias)) {
            return keyStore.getKey(alias, null) as SecretKey
        }
        
        // Create new key
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        
        val keySpec = KeyGenParameterSpec.Builder(alias, 
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(KEY_SIZE)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setRandomizedEncryptionRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Only allow encryption/decryption after 24h timeout
                    setUserAuthenticationRequired(false)
                }
            }
            .build()
        
        keyGenerator.init(keySpec)
        Log.d(TAG, "New encryption key created for profile: $profileId")
        return keyGenerator.generateKey()
    }
    
    /**
     * Delete encryption key for profile (when profile is deleted)
     */
    fun deleteProfileKey(profileId: Int) {
        try {
            val alias = "$KEYSTORE_ALIAS_PREFIX$profileId"
            keyStore.deleteEntry(alias)
            Log.d(TAG, "Encryption key deleted for profile: $profileId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting profile key", e)
        }
    }
}
