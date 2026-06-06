package com.multispace.core.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.multispace.MultiSpaceApplication
import kotlinx.coroutines.launch

/**
 * ViewModel for managing profile list and operations
 * Bridges profile manager with UI layer
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ProfileViewModel"
    private val multiSpaceApp = application as MultiSpaceApplication
    private val profileManager = multiSpaceApp.getProfileManager()
    private val lifecycleController = multiSpaceApp.getProfileLifecycleController()
    
    private val _profiles = MutableLiveData<List<ProfileModel>>()
    val profiles: LiveData<List<ProfileModel>> = _profiles
    
    private val _selectedProfile = MutableLiveData<ProfileModel?>()
    val selectedProfile: LiveData<ProfileModel?> = _selectedProfile
    
    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private val _operationResult = MutableLiveData<OperationResult?>()
    val operationResult: LiveData<OperationResult?> = _operationResult
    
    init {
        loadProfiles()
    }
    
    /**
     * Load profiles for current user
     */
    fun loadProfiles(userId: Int = 1) {
        viewModelScope.launch {
            try {
                _loading.postValue(true)
                _error.postValue(null)
                
                profileManager.getProfilesForUser(userId).collect { profileList ->
                    _profiles.postValue(profileList)
                }
                
                Log.d(TAG, "Profiles loaded: ${_profiles.value?.size ?: 0}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profiles", e)
                _error.postValue(e.message ?: "Unknown error loading profiles")
            } finally {
                _loading.postValue(false)
            }
        }
    }
    
    /**
     * Create new profile
     */
    fun createProfile(
        userId: Int = 1,
        profileName: String,
        googleAccount: String? = null,
        isEncrypted: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                _loading.postValue(true)
                _error.postValue(null)
                
                val request = CreateProfileRequest(
                    userId = userId,
                    profileName = profileName,
                    googleAccount = googleAccount,
                    isEncrypted = isEncrypted
                )
                
                val result = profileManager.createProfile(request)
                result.onSuccess { profile ->
                    Log.d(TAG, "Profile created: ${profile.profileName}")
                    _operationResult.postValue(OperationResult.Success(profile))
                    loadProfiles(userId)
                }.onFailure { e ->
                    Log.e(TAG, "Error creating profile", e)
                    _error.postValue(e.message ?: "Failed to create profile")
                    _operationResult.postValue(OperationResult.Error(e))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception creating profile", e)
                _error.postValue(e.message)
            } finally {
                _loading.postValue(false)
            }
        }
    }
    
    /**
     * Activate profile
     */
    fun activateProfile(profileId: Int) {
        viewModelScope.launch {
            try {
                _loading.postValue(true)
                _error.postValue(null)
                
                val result = lifecycleController.activateProfile(profileId)
                result.onSuccess { profile ->
                    Log.d(TAG, "Profile activated: ${profile.profileName}")
                    _selectedProfile.postValue(profile)
                    _operationResult.postValue(OperationResult.Success(profile))
                }.onFailure { e ->
                    Log.e(TAG, "Error activating profile", e)
                    _error.postValue(e.message ?: "Failed to activate profile")
                    _operationResult.postValue(OperationResult.Error(e))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception activating profile", e)
                _error.postValue(e.message)
            } finally {
                _loading.postValue(false)
            }
        }
    }
    
    /**
     * Suspend profile
     */
    fun suspendProfile(profileId: Int) {
        viewModelScope.launch {
            try {
                _loading.postValue(true)
                _error.postValue(null)
                
                val result = lifecycleController.suspendProfile(profileId)
                result.onSuccess { profile ->
                    Log.d(TAG, "Profile suspended: ${profile.profileName}")
                    _operationResult.postValue(OperationResult.Success(profile))
                    loadProfiles()
                }.onFailure { e ->
                    Log.e(TAG, "Error suspending profile", e)
                    _error.postValue(e.message ?: "Failed to suspend profile")
                    _operationResult.postValue(OperationResult.Error(e))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception suspending profile", e)
                _error.postValue(e.message)
            } finally {
                _loading.postValue(false)
            }
        }
    }
    
    /**
     * Hibernate profile
     */
    fun hibernateProfile(profileId: Int) {
        viewModelScope.launch {
            try {
                _loading.postValue(true)
                
                val result = lifecycleController.hibernateProfile(profileId)
                result.onSuccess { profile ->
                    Log.d(TAG, "Profile hibernated: ${profile.profileName}")
                    _operationResult.postValue(OperationResult.Success(profile))
                    loadProfiles()
                }.onFailure { e ->
                    Log.e(TAG, "Error hibernating profile", e)
                    _error.postValue(e.message ?: "Failed to hibernate profile")
                    _operationResult.postValue(OperationResult.Error(e))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception hibernating profile", e)
                _error.postValue(e.message)
            } finally {
                _loading.postValue(false)
            }
        }
    }
    
    /**
     * Delete profile
     */
    fun deleteProfile(profileId: Int) {
        viewModelScope.launch {
            try {
                _loading.postValue(true)
                _error.postValue(null)
                
                val result = profileManager.deleteProfile(profileId)
                result.onSuccess {
                    Log.d(TAG, "Profile deleted: $profileId")
                    _operationResult.postValue(OperationResult.Success(null))
                    loadProfiles()
                }.onFailure { e ->
                    Log.e(TAG, "Error deleting profile", e)
                    _error.postValue(e.message ?: "Failed to delete profile")
                    _operationResult.postValue(OperationResult.Error(e))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception deleting profile", e)
                _error.postValue(e.message)
            } finally {
                _loading.postValue(false)
            }
        }
    }
    
    /**
     * Select profile for viewing details
     */
    fun selectProfile(profile: ProfileModel) {
        _selectedProfile.postValue(profile)
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.postValue(null)
    }
    
    /**
     * Clear operation result
     */
    fun clearOperationResult() {
        _operationResult.postValue(null)
    }
}

/**
 * Result of profile operations
 */
sealed class OperationResult {
    data class Success(val data: Any?) : OperationResult()
    data class Error(val exception: Exception) : OperationResult()
}
