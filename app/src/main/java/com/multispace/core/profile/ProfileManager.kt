package com.multispace.core.profile

import android.content.Context
import android.util.Log
import com.multispace.data.local.MultiSpaceDatabase
import com.multispace.data.local.ProfileEntity
import com.multispace.data.local.ProfileState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.*

/**
 * Central manager for profile CRUD operations and lifecycle
 * Handles profile creation, deletion, state management
 */
class ProfileManager(
    private val context: Context,
    private val database: MultiSpaceDatabase
) {
    private val TAG = "ProfileManager"
    private val profileDao = database.profileDao()
    private val profileRomBasePath = File(context.filesDir, "profiles").absolutePath
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Create a new profile with forced Google account setup
     */
    suspend fun createProfile(request: CreateProfileRequest): Result<ProfileModel> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating profile: ${request.profileName}")
            
            // Generate unique identifiers
            val androidId = generateUniqueAndroidId()
            val imei = generateUniqueImei()
            val macAddress = generateUniqueMacAddress()
            val serialNumber = generateUniqueSerialNumber()
            
            val romPath = File(profileRomBasePath, "profile_${System.currentTimeMillis()}").absolutePath
            
            val entity = ProfileEntity(
                userId = request.userId,
                profileName = request.profileName,
                googleAccount = request.googleAccount,
                androidId = androidId,
                gsfId = null, // Will be assigned after GSF bootstrap
                imei = imei,
                macAddress = macAddress,
                serialNumber = serialNumber,
                gsfTokenEncrypted = null,
                accountTokenEncrypted = null,
                state = ProfileState.ACTIVE.name,
                lastAccessedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                romPath = romPath,
                isEncrypted = request.isEncrypted
            )
            
            // Create ROM directory
            File(romPath).mkdirs()
            
            val profileId = profileDao.insertProfile(entity)
            Log.d(TAG, "Profile created with ID: $profileId")
            
            val model = entity.copy(id = profileId.toInt()).toModel()
            Result.success(model)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating profile", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get profile by ID
     */
    suspend fun getProfileById(profileId: Int): ProfileModel? = withContext(Dispatchers.IO) {
        profileDao.getProfileById(profileId)?.toModel()
    }
    
    /**
     * Get all profiles for a user
     */
    fun getProfilesForUser(userId: Int): Flow<List<ProfileModel>> {
        return profileDao.getProfilesByUser(userId).map { entities ->
            entities.map { it.toModel() }
        }
    }
    
    /**
     * Get all profiles across all users
     */
    suspend fun getAllProfiles(): List<ProfileModel> = withContext(Dispatchers.IO) {
        profileDao.getProfilesByState(ProfileState.ACTIVE.name).map { it.toModel() } +
        profileDao.getProfilesByState(ProfileState.SUSPENDED.name).map { it.toModel() } +
        profileDao.getProfilesByState(ProfileState.DORMANT.name).map { it.toModel() }
    }
    
    /**
     * Update profile
     */
    suspend fun updateProfile(profile: ProfileModel): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = profile.toEntity()
            profileDao.updateProfile(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete profile
     */
    suspend fun deleteProfile(profileId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val profile = profileDao.getProfileById(profileId) ?: return@withContext Result.failure(Exception("Profile not found"))
            profileDao.deleteProfile(profile)
            
            // Clean up ROM directory
            val romDir = File(profile.romPath)
            if (romDir.exists()) {
                romDir.deleteRecursively()
            }
            
            Log.d(TAG, "Profile deleted: $profileId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting profile: $profileId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get profile count for user
     */
    suspend fun getProfileCountForUser(userId: Int): Int = withContext(Dispatchers.IO) {
        profileDao.getProfileCountForUser(userId)
    }
    
    /**
     * Get active profiles for user
     */
    suspend fun getActiveProfilesForUser(userId: Int): List<ProfileModel> = withContext(Dispatchers.IO) {
        profileDao.getActiveProfilesForUser(userId, ProfileState.ACTIVE.name).map { it.toModel() }
    }
    
    /**
     * Update GSF token (encrypted)
     */
    suspend fun updateGsfToken(profileId: Int, tokenEncrypted: String, gsfId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val profile = profileDao.getProfileById(profileId) ?: return@withContext Result.failure(Exception("Profile not found"))
            profileDao.updateProfile(profile.copy(gsfTokenEncrypted = tokenEncrypted, gsfId = gsfId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get profile by Android ID (used for identity verification)
     */
    suspend fun getProfileByAndroidId(androidId: String): ProfileModel? = withContext(Dispatchers.IO) {
        profileDao.getProfileByAndroidId(androidId)?.toModel()
    }
    
    /**
     * Generate unique Android ID (format: hex string similar to real Android ID)
     */
    private fun generateUniqueAndroidId(): String {
        return UUID.randomUUID().toString().replace("-", "").take(16)
    }
    
    /**
     * Generate unique IMEI (15 digits)
     */
    private fun generateUniqueImei(): String {
        return (0..14).map { (0..9).random() }.joinToString("")
    }
    
    /**
     * Generate unique MAC address
     */
    private fun generateUniqueMacAddress(): String {
        return (0..5).map { String.format("%02x", Random().nextInt(256)) }.joinToString(":")
    }
    
    /**
     * Generate unique serial number
     */
    private fun generateUniqueSerialNumber(): String {
        return UUID.randomUUID().toString().take(20).toUpperCase()
    }
    
    fun close() {
        scope.cancel()
    }
}

// Extension functions for entity/model conversion
fun ProfileEntity.toModel(): ProfileModel {
    return ProfileModel(
        id = id,
        userId = userId,
        profileName = profileName,
        googleAccount = googleAccount,
        androidId = androidId,
        gsfId = gsfId,
        imei = imei,
        macAddress = macAddress,
        serialNumber = serialNumber,
        gsfTokenEncrypted = gsfTokenEncrypted,
        accountTokenEncrypted = accountTokenEncrypted,
        state = ProfileState.valueOf(state),
        lastAccessedAt = lastAccessedAt,
        createdAt = createdAt,
        romPath = romPath,
        isEncrypted = isEncrypted,
        encryptionKeyHash = encryptionKeyHash
    )
}

fun ProfileModel.toEntity(): ProfileEntity {
    return ProfileEntity(
        id = id,
        userId = userId,
        profileName = profileName,
        googleAccount = googleAccount,
        androidId = androidId,
        gsfId = gsfId,
        imei = imei,
        macAddress = macAddress,
        serialNumber = serialNumber,
        gsfTokenEncrypted = gsfTokenEncrypted,
        accountTokenEncrypted = accountTokenEncrypted,
        state = state.name,
        lastAccessedAt = lastAccessedAt,
        createdAt = createdAt,
        romPath = romPath,
        isEncrypted = isEncrypted,
        encryptionKeyHash = encryptionKeyHash
    )
}
