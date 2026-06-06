package com.multispace.core.profile

import android.content.Context
import android.util.Log
import com.multispace.data.local.ProfileEntity
import com.multispace.data.local.ProfileState
import kotlinx.coroutines.*
import java.io.File
import java.util.*

/**
 * Manages profile lifecycle state transitions: ACTIVE -> SUSPENDED -> DORMANT
 * - ACTIVE: GSF running, apps usable, in RAM
 * - SUSPENDED: GSF frozen, state saved to ROM, minimal RAM
 * - DORMANT: Fully serialized to ROM, zero RAM usage
 */
class ProfileLifecycleController(
    private val context: Context,
    private val profileManager: ProfileManager
) {
    private val TAG = "ProfileLifecycleController"
    private val profileRomBasePath = File(context.filesDir, "profiles").absolutePath
    
    /**
     * Transition profile from DORMANT/SUSPENDED to ACTIVE
     * Loads state from ROM if necessary
     */
    suspend fun activateProfile(profileId: Int): Result<ProfileModel> = withContext(Dispatchers.IO) {
        try {
            val profile = profileManager.getProfileById(profileId)
                ?: return@withContext Result.failure(Exception("Profile not found: $profileId"))
            
            Log.d(TAG, "Activating profile: ${profile.profileName} (current state: ${profile.state})")
            
            // If DORMANT, deserialize from ROM
            if (profile.state == ProfileState.DORMANT) {
                Log.d(TAG, "Deserializing profile from ROM: ${profile.romPath}")
                // Deserialize logic will be implemented by ProfileSerializer
            }
            
            // Transition to ACTIVE
            val updated = profile.copy(state = ProfileState.ACTIVE, lastAccessedAt = System.currentTimeMillis())
            profileManager.updateProfile(updated)
            
            Log.d(TAG, "Profile activated: ${profile.profileName}")
            Result.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Error activating profile: $profileId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Transition profile from ACTIVE to SUSPENDED
     * Saves critical state to ROM, frees RAM
     */
    suspend fun suspendProfile(profileId: Int): Result<ProfileModel> = withContext(Dispatchers.IO) {
        try {
            val profile = profileManager.getProfileById(profileId)
                ?: return@withContext Result.failure(Exception("Profile not found: $profileId"))
            
            Log.d(TAG, "Suspending profile: ${profile.profileName}")
            
            // Checkpoint state to ROM (GSF tokens, account state)
            checkpointToRom(profile)
            
            // Transition to SUSPENDED
            val updated = profile.copy(state = ProfileState.SUSPENDED, lastAccessedAt = System.currentTimeMillis())
            profileManager.updateProfile(updated)
            
            Log.d(TAG, "Profile suspended: ${profile.profileName}")
            Result.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Error suspending profile: $profileId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Transition profile from SUSPENDED to DORMANT
     * Full serialization to ROM, zero RAM footprint
     */
    suspend fun hibernateProfile(profileId: Int): Result<ProfileModel> = withContext(Dispatchers.IO) {
        try {
            val profile = profileManager.getProfileById(profileId)
                ?: return@withContext Result.failure(Exception("Profile not found: $profileId"))
            
            Log.d(TAG, "Hibernating profile: ${profile.profileName}")
            
            // Full serialization to ROM
            checkpointToRom(profile)
            
            // Transition to DORMANT
            val updated = profile.copy(state = ProfileState.DORMANT, lastAccessedAt = System.currentTimeMillis())
            profileManager.updateProfile(updated)
            
            Log.d(TAG, "Profile hibernated (dormant): ${profile.profileName}")
            Result.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Error hibernating profile: $profileId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if profile should auto-suspend based on inactivity
     */
    suspend fun evaluateProfileStates() = withContext(Dispatchers.IO) {
        try {
            val allProfiles = profileManager.getAllProfiles()
            val now = System.currentTimeMillis()
            val SUSPENSION_THRESHOLD = 5 * 60 * 1000 // 5 minutes of inactivity
            val HIBERNATION_THRESHOLD = 24 * 60 * 60 * 1000 // 24 hours
            
            for (profile in allProfiles) {
                if (profile.state == ProfileState.ACTIVE) {
                    val inactiveTime = now - profile.lastAccessedAt
                    
                    if (inactiveTime > HIBERNATION_THRESHOLD) {
                        Log.d(TAG, "Auto-hibernating inactive profile: ${profile.profileName}")
                        hibernateProfile(profile.id)
                    } else if (inactiveTime > SUSPENSION_THRESHOLD) {
                        Log.d(TAG, "Auto-suspending inactive profile: ${profile.profileName}")
                        suspendProfile(profile.id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating profile states", e)
        }
    }
    
    /**
     * Save profile state snapshot to ROM
     * Implementation delegated to ProfileSerializer
     */
    private suspend fun checkpointToRom(profile: ProfileModel) {
        val romPath = File(profileRomBasePath, "profile_${profile.id}")
        if (!romPath.exists()) {
            romPath.mkdirs()
        }
        Log.d(TAG, "Checkpointing profile to ROM: ${romPath.absolutePath}")
        // ProfileSerializer will handle the actual serialization
    }
}
