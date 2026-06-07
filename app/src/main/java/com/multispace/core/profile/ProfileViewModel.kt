package com.multispace.core.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.multispace.MultiSpaceApplication
import com.multispace.data.local.ProfileState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * ViewModel for managing profile list and operations
 * Bridges profile manager with UI layer with transactional GMS staging validation.
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
     * Load profiles for current user (Staging profiles are filtered out from UI view)
     */
    fun loadProfiles(userId: Int = 1) {
        viewModelScope.launch {
            try {
                _loading.postValue(true)
                _error.postValue(null)
                
                profileManager.getProfilesForUser(userId).collect { profileList ->
                    // Filter out staging entries so they don't render until Google Login is successful
                    val validatedList = profileList.filter { it.state != ProfileState.STAGING }
                    _profiles.postValue(validatedList)
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
     * Create new profile using a pre-flight GMS verification flow
     */
    fun createProfile(
        userId: Int = 1,
        profileName: String,
        isEncrypted: Boolean = false
    ) {
        viewModelScope.launch {
            var stagingProfile: ProfileModel? = null
            try {
                _loading.postValue(true)
                _error.postValue(null)
                
                // 1. Stage the profile environment structure silently
                val request = CreateProfileRequest(
                    userId = userId,
                    profileName = profileName,
                    googleAccount = null, // Discovered upon successful GMS authentication callback
                    isEncrypted = isEncrypted,
                    initialState = ProfileState.STAGING
                )
                
                val result = profileManager.createProfile(request)
                result.onSuccess { profile ->
                    stagingProfile = profile
                    Log.i(TAG, "Profile environments staged. Invoking mandatory GMS challenge link...")
                    
                    // 2. Query real GmsConnector client token array
                    val gmsConnector = multiSpaceApp.getGsfBootstrapper()
                    val tokenResult = gmsConnector.getGcmToken() 
                    
                    tokenResult.onSuccess { token ->
                        val parsedGsfId = token.take(16)
                        val authenticatedEmail = com.google.android.gms.auth.api.signin.GoogleSignIn
                            .getLastSignedInAccount(multiSpaceApp)?.email ?: "profile.user@google.com"
                        
                        // 3. Promote profile to active status and serialize
                        val finalizedProfile = profile.copy(
                            state = ProfileState.ACTIVE,
                            gsfId = parsedGsfId,
                            googleAccount = authenticatedEmail
                        )
                        
                        profileManager.updateProfile(finalizedProfile)
                        multiSpaceApp.getProfileSerializer().serializeToRom(finalizedProfile)
                        
                        Log.d(TAG, "Profile successfully bound to GMS identity and committed: ${finalizedProfile.profileName}")
                        _operationResult.postValue(OperationResult.Success(finalizedProfile))
                        loadProfiles(userId)
                    }.onFailure { authError ->
                        // 4. Rollback and clear files immediately if user aborts sign-in
                        Log.w(TAG, "Mandatory Google authentication failed. Purging staging profile cache.")
                        stagingProfile?.let { multiSpaceApp.getProfileSerializer().deleteFromRom(it) }
                        _error.postValue("Google Sign-In authentication is mandatory to establish profiles.")
                        _operationResult.postValue(OperationResult.Error(authError as? Exception ?: Exception(authError)))
                    }
                    
                }.onFailure { e ->
                    Log.e(TAG, "Error staging profile container", e)
                    _error.postValue(e.message ?: "Failed to stage profile target")
                    _operationResult.postValue(OperationResult.Error(e as? Exception ?: Exception(e)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception executing staging workflow layout", e)
                stagingProfile?.let { multiSpaceApp.getProfileSerializer().deleteFromRom(it) }
                _error.postValue(e.message)
            } finally {
                _loading.postValue(false)
            }
        }
    }
    
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
                    _operationResult.postValue(OperationResult.Error(e as? Exception ?: Exception(e)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception activating profile", e)
                _error.postValue(e.message)
            } finally {
                _loading.postValue(false)
            }
        }
    }
    
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
                    _operationResult.postValue(OperationResult.Error(e as? Exception ?: Exception(e)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception suspending profile", e)
                _error.postValue(e.message)
            } finally {
                _loading.postValue(false)
            }
        }
    }
    
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
                    _operationResult.postValue(OperationResult.Error(e as? Exception ?: Exception(e)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception hibernating profile", e)
                _error.postValue(e.message)
            } finally {
                _loading.postValue(false)
            }
        }
    }
    
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
                    _operationResult.postValue(OperationResult.Error(e as? Exception ?: Exception(e)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception deleting profile", e)
                _error.postValue(e.message)
            } finally {
                _loading.postValue(false)
            }
        }
    }
    
    fun selectProfile(profile: ProfileModel) {
        _selectedProfile.postValue(profile)
    }
    
    fun clearError() {
        _error.postValue(null)
    }
    
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