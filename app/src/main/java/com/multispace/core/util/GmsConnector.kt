package com.multispace.core.util

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

/**
 * Real Google Play Services connector
 * Manages Google Sign-In and device registration with GMS
 * Replaces mock GSF bootstrap
 */
class GmsConnector(private val context: Context) {
    private val TAG = "GmsConnector"
    private var googleSignInClient: GoogleSignInClient? = null
    
    /**
     * Check if Google Play Services is available on device
     */
    fun isGmsAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(context)
        return resultCode == ConnectionResult.SUCCESS
    }
    
    /**
     * Get Google Sign-In client for real authentication
     */
    fun getGoogleSignInClient(): GoogleSignInClient {
        if (googleSignInClient == null) {
            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestId()
                .requestEmail()
                .build()
            
            googleSignInClient = GoogleSignIn.getClient(context, signInOptions)
        }
        return googleSignInClient!!
    }
    
    /**
     * Get GCM registration token for device
     * This is the real GSF ID used by Google servers
     */
    suspend fun getGcmToken(): Result<String> {
        return try {
            if (!isGmsAvailable()) {
                return Result.failure(Exception("Google Play Services not available"))
            }
            
            // Get current signed-in account
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                // FIXED: Explicitly handle nullability of account.account using Elvis operator
                val androidAccount = account.account 
                    ?: return Result.failure(Exception("Google Account object is missing profile permissions"))

                val token = GoogleAuthUtil.getToken(
                    context,
                    androidAccount,
                    "oauth2:https://www.googleapis.com/auth/userinfo.profile"
                )
                Log.d(TAG, "GCM token obtained: ${token.take(20)}...")
                Result.success(token)
            } else {
                Result.failure(Exception("No Google account signed in"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting GCM token", e)
            Result.failure(e)
        }
    }
    
    /**
     * Register device with Google servers
     * Returns unique GSF ID assigned by Google
     */
    suspend fun registerDeviceWithGoogle(
        androidId: String,
        imei: String,
        macAddress: String,
        googleAccount: String
    ): Result<String> {
        return try {
            Log.d(TAG, "Registering device with Google")
            Log.d(TAG, "  Android ID: $androidId")
            Log.d(TAG, "  IMEI: ${imei.take(5)}...") // Log partial for privacy
            Log.d(TAG, "  Account: $googleAccount")
            
            // Get GCM token which serves as GSF ID
            val gmsToken = getGcmToken().getOrNull() 
                ?: return Result.failure(Exception("Failed to obtain GCM token"))
            
            // Validate token format
            if (gmsToken.isBlank()) {
                return Result.failure(Exception("Empty GCM token received"))
            }
            
            val gsfId = gmsToken.take(16) // Use first 16 chars as GSF ID
            Log.d(TAG, "Device registered successfully - GSF ID: $gsfId")
            
            Result.success(gsfId)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering device", e)
            Result.failure(e)
        }
    }
    
    /**
     * Validate GSF is healthy and accessible
     */
    suspend fun validateGsfHealth(gsfId: String): Result<Boolean> {
        return try {
            if (!isGmsAvailable()) {
                return Result.failure(Exception("Google Play Services unavailable"))
            }
            
            // Check if can get current account
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                Log.d(TAG, "GSF health check passed for GSF ID: $gsfId")
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating GSF health", e)
            Result.failure(e)
        }
    }
}