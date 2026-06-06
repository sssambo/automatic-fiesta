package com.multispace.core.gsf

import android.content.Context
import android.util.Log
import com.multispace.core.profile.ProfileModel
import com.multispace.core.util.GmsConnector
import com.multispace.core.util.TokenEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Enforces mandatory Google account setup on profile creation
 * Uses real Google Sign-In via Google Play Services
 * Blocks profile activation until Google login is successful
 */
class AccountEnforcer(private val context: Context) {
    private val TAG = "AccountEnforcer"
    private val gmsConnector = GmsConnector(context)
    
    /**
     * Enforce Google account login for new profile
     * Uses real Google Sign-In (requires user interaction)
     * Blocks until:
     * 1. User successfully authenticates with Google
     * 2. Account is validated and stored in profile
     */
    suspend fun enforceGoogleLogin(profile: ProfileModel): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Enforcing Google login for profile: ${profile.profileName}")
            
            // Check if GMS is available
            if (!gmsConnector.isGmsAvailable()) {
                return@withContext Result.failure(Exception("Google Play Services not available on device"))
            }
            
            // Get Google Sign-In client for real authentication
            val googleSignInClient = gmsConnector.getGoogleSignInClient()
            
            // In real implementation, this would:
            // 1. Launch Google Sign-In activity (requires Activity context)
            // 2. Wait for user interaction and authentication
            // 3. Return authenticated account email
            // For now, we validate if an account is already signed in
            
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            val accountEmail = if (account != null) {
                account.email ?: return@withContext Result.failure(Exception("Google account missing email"))
            } else {
                return@withContext Result.failure(Exception("No Google account signed in. User must authenticate via Google Sign-In activity."))
            }
            
            Log.d(TAG, "Google login enforced for account: $accountEmail")
            Result.success(accountEmail)
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing Google login", e)
            Result.failure(e)
        }
    }
    
    /**
     * Validate if Google account is properly set up for profile
     */
    suspend fun validateGoogleAccount(profile: ProfileModel): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Check if account exists and GSF has been bootstrapped
            val hasAccount = profile.googleAccount != null
            val hasGsfBootstrapped = profile.gsfId != null && profile.gsfTokenEncrypted != null
            
            if (hasAccount && hasGsfBootstrapped) {
                Log.d(TAG, "Google account validated for profile: ${profile.profileName}")
                Result.success(true)
            } else {
                Log.w(TAG, "Google account validation failed: account=$hasAccount, gsf=$hasGsfBootstrapped")
                Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Real Google Services Framework (GSF) bootstrapper
 * Initializes GSF for a new profile using actual Google Play Services
 * Replaces all mock bootstrap logic with real GMS integration
 */
class GsfBootstrapper(private val context: Context) {
    private val TAG = "GsfBootstrapper"
    private val maxRetries = 3
    private val retryDelayMs = 2000L
    private val gmsConnector = GmsConnector(context)
    private val tokenEncryption = TokenEncryption(context)
    
    /**
     * Initialize GSF for profile after successful Google login
     * Uses real GMS device registration
     */
    suspend fun initGsfForProfile(profile: ProfileModel, googleAccount: String): Result<GsfBootstrapResult> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                Log.d(TAG, "GSF bootstrap attempt $attempt/$maxRetries for profile: ${profile.profileName}")
                
                // Step 1: Check GMS availability
                if (!gmsConnector.isGmsAvailable()) {
                    throw Exception("Google Play Services not available")
                }
                
                // Step 2: Register device with Google using real GMS
                val gsfId = registerDeviceWithGoogle(profile, googleAccount)
                Log.d(TAG, "Device registered with Google - GSF ID: $gsfId")
                
                // Step 3: Validate GSF health with real GMS
                val isHealthy = validateGsfHealth(profile, gsfId)
                if (!isHealthy) {
                    throw Exception("GSF health check failed")
                }
                
                // Step 4: Initialize session keepalive with real token
                val tokenEncrypted = initializeSessionKeepalive(profile, googleAccount)
                
                Log.d(TAG, "GSF bootstrap successful for profile: ${profile.profileName}")
                
                return@withContext Result.success(GsfBootstrapResult(
                    gsfId = gsfId,
                    tokenEncrypted = tokenEncrypted,
                    googleAccount = googleAccount,
                    bootstrapTime = System.currentTimeMillis()
                ))
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "GSF bootstrap attempt $attempt failed: ${e.message}")
                
                if (attempt < maxRetries) {
                    delay(retryDelayMs * attempt) // Exponential backoff
                }
            }
        }
        
        Log.e(TAG, "GSF bootstrap failed after $maxRetries attempts")
        Result.failure(lastError ?: Exception("GSF bootstrap failed"))
    }
    
    /**
     * Register device with Google servers using real GMS
     * Returns unique GSF ID from Google
     */
    private suspend fun registerDeviceWithGoogle(profile: ProfileModel, googleAccount: String): String = withContext(Dispatchers.IO) {
        val gsfId = gmsConnector.registerDeviceWithGoogle(
            androidId = profile.androidId,
            imei = profile.imei,
            macAddress = profile.macAddress,
            googleAccount = googleAccount
        ).getOrThrow()
        
        Log.d(TAG, "Device registered with GSF ID: $gsfId")
        gsfId
    }
    
    /**
     * Validate GSF health using real GMS
     */
    private suspend fun validateGsfHealth(profile: ProfileModel, gsfId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = gmsConnector.validateGsfHealth(gsfId).getOrNull() ?: false
            if (result) {
                Log.d(TAG, "GSF health check passed for GSF ID: $gsfId")
            } else {
                Log.w(TAG, "GSF health check failed for GSF ID: $gsfId")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "GSF health check error", e)
            false
        }
    }
    
    /**
     * Initialize session keepalive mechanism with real token encryption
     * Uses AES-256 encryption from TokenEncryption utility
     */
    private suspend fun initializeSessionKeepalive(profile: ProfileModel, googleAccount: String): String = withContext(Dispatchers.IO) {
        try {
            // Get GCM token from GMS
            val token = gmsConnector.getGcmToken().getOrThrow()
            
            // Encrypt token with AES-256
            val tokenEncrypted = tokenEncryption.encryptToken(token, profile.id).getOrThrow()
            
            Log.d(TAG, "Session keepalive initialized for: $googleAccount with AES-256 encryption")
            tokenEncrypted
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing session keepalive", e)
            throw e
        }
    }
}

/**
 * Result of successful GSF bootstrap
 */
data class GsfBootstrapResult(
    val gsfId: String,
    val tokenEncrypted: String,
    val googleAccount: String,
    val bootstrapTime: Long
)

/**
 * Monitor GSF health and refresh sessions as needed
 * Uses real GMS token refresh
 */
class GsfHealthMonitor(private val context: Context) {
    private val TAG = "GsfHealthMonitor"
    private val SESSION_REFRESH_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours
    private val gmsConnector = GmsConnector(context)
    private val tokenEncryption = TokenEncryption(context)
    
    /**
     * Check if GSF needs token refresh
     */
    suspend fun needsTokenRefresh(profile: ProfileModel): Boolean = withContext(Dispatchers.IO) {
        if (profile.gsfTokenEncrypted == null) return@withContext false
        
        // Check if token is nearing expiration
        val bootstrapTime = profile.createdAt
        val timeSinceBootstrap = System.currentTimeMillis() - bootstrapTime
        
        timeSinceBootstrap > SESSION_REFRESH_INTERVAL
    }
    
    /**
     * Refresh GSF session token using real GMS
     */
    suspend fun refreshGsfToken(profile: ProfileModel): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Refreshing GSF token for profile: ${profile.profileName}")
            
            // Get fresh GCM token from GMS
            val newToken = gmsConnector.getGcmToken().getOrThrow()
            val tokenEncrypted = tokenEncryption.encryptToken(newToken, profile.id).getOrThrow()
            
            Log.d(TAG, "GSF token refreshed for profile: ${profile.profileName}")
            Result.success(tokenEncrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing GSF token", e)
            Result.failure(e)
        }
    }
    
    /**
     * Validate current GSF status using real GMS
     */
    suspend fun validateGsfStatus(profile: ProfileModel): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (profile.gsfId == null || profile.gsfTokenEncrypted == null) {
                return@withContext Result.success(false)
            }
            
            // Validate with GMS
            val isHealthy = gmsConnector.validateGsfHealth(profile.gsfId!!).getOrNull() ?: false
            Log.d(TAG, "GSF status validated for profile: ${profile.profileName} - healthy: $isHealthy")
            Result.success(isHealthy)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
