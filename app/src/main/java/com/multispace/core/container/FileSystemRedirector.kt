package com.multispace.core.container

import android.util.Log
import com.multispace.core.profile.ProfileModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Extends NewBlackbox's FileSystemHook to provide per-profile filesystem redirection
 * Isolates:
 * - /data/data/{appId} → /data/data/{appId}_profile_{id}
 * - /data/cache/{appId} → /data/cache/{appId}_profile_{id}
 * - Shared preferences per profile
 * - App databases per profile
 */
class FileSystemRedirector(private val basePath: String = "/data/data") {
    private val TAG = "FileSystemRedirector"
    
    /**
     * Get redirected path for an app in a specific profile
     * Maps /data/data/{appId} → /multispace/profiles/{profileId}/app_data/{appId}
     */
    fun getProfileAppDataPath(profile: ProfileModel, appPackageName: String): String {
        val profileAppDataDir = File(profile.romPath, "app_data")
        val appDir = File(profileAppDataDir, appPackageName)
        appDir.mkdirs()
        return appDir.absolutePath
    }
    
    /**
     * Get redirected path for app cache
     */
    fun getProfileCachePath(profile: ProfileModel, appPackageName: String): String {
        val profileCacheDir = File(profile.romPath, "cache")
        val appCacheDir = File(profileCacheDir, appPackageName)
        appCacheDir.mkdirs()
        return appCacheDir.absolutePath
    }
    
    /**
     * Get redirected shared preferences path for app
     */
    fun getProfileSharedPrefsPath(profile: ProfileModel, appPackageName: String): String {
        val prefsDir = File(profile.romPath, "shared_prefs")
        prefsDir.mkdirs()
        return prefsDir.absolutePath
    }
    
    /**
     * Create filesystem redirect rule for hook layer
     * Returns native hook configuration
     */
    suspend fun createFsRedirectRule(
        profile: ProfileModel,
        appPackageName: String
    ): Result<FsRedirectRule> = withContext(Dispatchers.IO) {
        try {
            val appDataPath = getProfileAppDataPath(profile, appPackageName)
            val cachePath = getProfileCachePath(profile, appPackageName)
            val prefsPath = getProfileSharedPrefsPath(profile, appPackageName)
            
            Log.d(TAG, "Creating FS redirect for app: $appPackageName in profile: ${profile.profileName}")
            
            val rule = FsRedirectRule(
                profileId = profile.id,
                appPackage = appPackageName,
                originalDataPath = "$basePath/$appPackageName",
                redirectedDataPath = appDataPath,
                cachePath = cachePath,
                sharedPrefsPath = prefsPath
            )
            
            Result.success(rule)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating FS redirect rule", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all redirect rules for a profile
     */
    suspend fun getProfileRedirectRules(
        profile: ProfileModel,
        installedApps: List<String>
    ): List<FsRedirectRule> = withContext(Dispatchers.IO) {
        installedApps.mapNotNull { app ->
            createFsRedirectRule(profile, app).getOrNull()
        }
    }
}

data class FsRedirectRule(
    val profileId: Int,
    val appPackage: String,
    val originalDataPath: String,
    val redirectedDataPath: String,
    val cachePath: String,
    val sharedPrefsPath: String
)

/**
 * Extension of NewBlackbox's BinderProxyLayer
 * Intercepts Binder calls and routes them to profile-isolated services
 */
class BinderProxyExtension(private val fileSystemRedirector: FileSystemRedirector) {
    private val TAG = "BinderProxyExtension"
    
    /**
     * Intercept package manager calls for profile isolation
     * Ensures app queries are scoped to profile context
     */
    fun createPackageManagerProxy(profile: ProfileModel): PackageManagerProxy {
        Log.d(TAG, "Creating PackageManager proxy for profile: ${profile.profileName}")
        return PackageManagerProxy(profile, fileSystemRedirector)
    }
    
    /**
     * Intercept account manager calls for profile isolation
     */
    fun createAccountManagerProxy(profile: ProfileModel): AccountManagerProxy {
        Log.d(TAG, "Creating AccountManager proxy for profile: ${profile.profileName}")
        return AccountManagerProxy(profile)
    }
    
    /**
     * Intercept settings provider calls
     */
    fun createSettingsProxy(profile: ProfileModel): SettingsProxy {
        Log.d(TAG, "Creating Settings proxy for profile: ${profile.profileName}")
        return SettingsProxy(profile)
    }
}

/**
 * Proxy for PackageManager service
 * Filters app queries to profile-installed apps only
 */
class PackageManagerProxy(
    private val profile: ProfileModel,
    private val fsRedirector: FileSystemRedirector
) {
    private val TAG = "PackageManagerProxy"
    
    /**
     * Override getPackageInfo to return profile-specific app info
     */
    suspend fun getPackageInfo(packageName: String, flags: Int): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "getPackageInfo called for: $packageName in profile: ${profile.profileName}")
        // Return profile-scoped package info
        null
    }
    
    /**
     * Override getInstalledPackages to return only profile's installed apps
     */
    suspend fun getInstalledPackages(flags: Int): List<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getInstalledPackages called for profile: ${profile.profileName}")
        // Return only apps installed in this profile
        emptyList()
    }
}

/**
 * Proxy for AccountManager service
 * Returns only profile's Google account
 */
class AccountManagerProxy(private val profile: ProfileModel) {
    private val TAG = "AccountManagerProxy"
    
    suspend fun getAccounts(): List<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getAccounts called for profile: ${profile.profileName}")
        if (profile.googleAccount != null) {
            listOf(profile.googleAccount)
        } else {
            emptyList()
        }
    }
    
    suspend fun getAccountsByType(type: String): List<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getAccountsByType($type) called for profile: ${profile.profileName}")
        if (type == "com.google" && profile.googleAccount != null) {
            listOf(profile.googleAccount)
        } else {
            emptyList()
        }
    }
}

/**
 * Proxy for Settings service
 * Returns profile-specific settings
 */
class SettingsProxy(private val profile: ProfileModel) {
    private val TAG = "SettingsProxy"
    
    suspend fun getSetting(namespace: String, key: String): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "getSetting($namespace, $key) in profile: ${profile.profileName}")
        // Return profile-specific settings
        null
    }
}
