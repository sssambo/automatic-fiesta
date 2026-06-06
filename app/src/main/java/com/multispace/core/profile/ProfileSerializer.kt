package com.multispace.core.profile

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.multispace.data.local.ProfileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

/**
 * Handles profile serialization/deserialization to/from ROM (persistent storage)
 * Saves GSF state, account tokens, and app data snapshots
 */
class ProfileSerializer(
    private val context: Context,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) {
    private val TAG = "ProfileSerializer"
    
    /**
     * Checkpoint profile state to ROM
     * Saves:
     * - GSF tokens (encrypted)
     * - Account state
     * - Filesystem snapshot metadata
     * - Device identity for profile
     */
    suspend fun serializeToRom(profile: ProfileModel): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val romDir = File(profile.romPath)
            if (!romDir.exists()) {
                romDir.mkdirs()
            }
            
            Log.d(TAG, "Serializing profile to ROM: ${profile.profileName} at ${profile.romPath}")
            
            // Save profile metadata
            val metadata = mapOf(
                "id" to profile.id,
                "name" to profile.profileName,
                "androidId" to profile.androidId,
                "gsfId" to profile.gsfId,
                "serializedAt" to System.currentTimeMillis(),
                "state" to profile.state.name
            )
            
            val metadataFile = File(romDir, "metadata.json")
            metadataFile.writeText(gson.toJson(metadata))
            
            // Save encrypted GSF state
            if (profile.gsfTokenEncrypted != null) {
                val gsfStateFile = File(romDir, "gsf_state.enc")
                val gsfState = mapOf(
                    "token" to profile.gsfTokenEncrypted,
                    "gsfId" to profile.gsfId,
                    "account" to profile.googleAccount,
                    "tokenRefreshTime" to System.currentTimeMillis()
                )
                gsfStateFile.writeText(gson.toJson(gsfState))
            }
            
            // Save device identity for this profile
            val identityFile = File(romDir, "identity.json")
            val identity = mapOf(
                "androidId" to profile.androidId,
                "imei" to profile.imei,
                "macAddress" to profile.macAddress,
                "serialNumber" to profile.serialNumber,
                "buildFingerprint" to generateDeviceFingerprint(profile)
            )
            identityFile.writeText(gson.toJson(identity))
            
            // Create app_data directory for app storage snapshots
            File(romDir, "app_data").mkdirs()
            File(romDir, "fs_snapshot").mkdirs()
            
            Log.d(TAG, "Profile serialization complete: ${profile.profileName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing profile", e)
            Result.failure(e)
        }
    }
    
    /**
     * Deserialize profile state from ROM
     * Restores GSF tokens, account state, and app data
     */
    suspend fun deserializeFromRom(profile: ProfileModel): Result<ProfileModel> = withContext(Dispatchers.IO) {
        try {
            val romDir = File(profile.romPath)
            if (!romDir.exists()) {
                return@withContext Result.failure(Exception("ROM directory not found: ${profile.romPath}"))
            }
            
            Log.d(TAG, "Deserializing profile from ROM: ${profile.profileName}")
            
            val metadataFile = File(romDir, "metadata.json")
            if (!metadataFile.exists()) {
                return@withContext Result.failure(Exception("Metadata not found in ROM"))
            }
            
            val metadata = gson.fromJson(metadataFile.readText(), Map::class.java)
            
            // Restore GSF state
            val gsfStateFile = File(romDir, "gsf_state.enc")
            var gsfToken: String? = null
            var gsfId: String? = null
            var googleAccount: String? = null
            
            if (gsfStateFile.exists()) {
                val gsfState = gson.fromJson(gsfStateFile.readText(), Map::class.java)
                gsfToken = gsfState["token"] as? String
                gsfId = gsfState["gsfId"] as? String
                googleAccount = gsfState["account"] as? String
            }
            
            // Restore device identity
            val identityFile = File(romDir, "identity.json")
            var restoredIdentity = profile.androidId
            if (identityFile.exists()) {
                val identity = gson.fromJson(identityFile.readText(), Map::class.java)
                restoredIdentity = (identity["androidId"] as? String) ?: profile.androidId
            }
            
            val restoredProfile = profile.copy(
                gsfTokenEncrypted = gsfToken,
                gsfId = gsfId,
                googleAccount = googleAccount ?: profile.googleAccount,
                androidId = restoredIdentity
            )
            
            Log.d(TAG, "Profile deserialization complete: ${profile.profileName}")
            Result.success(restoredProfile)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing profile", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete profile data from ROM
     */
    suspend fun deleteFromRom(profile: ProfileModel): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val romDir = File(profile.romPath)
            if (romDir.exists()) {
                romDir.deleteRecursively()
                Log.d(TAG, "Profile ROM deleted: ${profile.profileName}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting profile ROM", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get ROM directory size in bytes
     */
    suspend fun getRomSize(profile: ProfileModel): Long = withContext(Dispatchers.IO) {
        val romDir = File(profile.romPath)
        if (romDir.exists()) {
            romDir.walkTopDown().map { it.length() }.sum()
        } else {
            0L
        }
    }
    
    /**
     * Generate a realistic device fingerprint for this profile
     * Format: brand/product/device:version/build_id
     */
    private fun generateDeviceFingerprint(profile: ProfileModel): String {
        val brand = "Google"
        val product = "Pixel7"
        val device = "Pixel7"
        val version = "14"
        val buildId = UUID.randomUUID().toString().take(8).toUpperCase()
        return "$brand/$product/$device:$version/$buildId"
    }
    
    /**
     * Verify ROM integrity by checking critical files exist
     */
    suspend fun verifyRomIntegrity(profile: ProfileModel): Boolean = withContext(Dispatchers.IO) {
        try {
            val romDir = File(profile.romPath)
            if (!romDir.exists()) return@withContext false
            
            val metadataFile = File(romDir, "metadata.json")
            val identityFile = File(romDir, "identity.json")
            
            metadataFile.exists() && identityFile.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying ROM integrity", e)
            false
        }
    }
}

/**
 * ROM checkpoint data structure
 */
data class RomCheckpoint(
    val profileId: Int,
    val profileName: String,
    val androidId: String,
    val gsfId: String?,
    val serializedAt: Long,
    val romSize: Long,
    val state: ProfileState
)
