package com.multispace.core.container

import android.content.Context
import android.util.Log
import com.multispace.core.profile.ProfileModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Extends NewBlackbox's FileSystemHook to provide per-profile filesystem redirection
 * Fixes session dropping by separating persistent items into indestructible storage pools.
 */
class FileSystemRedirector(
    private val context: Context,
    private val basePath: String = "/data/data"
) {
    private val TAG = "FileSystemRedirector"
    
    /**
     * Maps volatile app directories inside the sandboxed file matrix
     */
    fun getProfileAppDataPath(profile: ProfileModel, appPackageName: String): String {
        val profileAppDataDir = File(profile.romPath, "app_data")
        val appDir = File(profileAppDataDir, appPackageName)
        if (!appDir.exists()) appDir.mkdirs()
        return appDir.absolutePath
    }
    
    /**
     * Maps cache directories cleanly
     */
    fun getProfileCachePath(profile: ProfileModel, appPackageName: String): String {
        val profileCacheDir = File(profile.romPath, "cache")
        val appCacheDir = File(profileCacheDir, appPackageName)
        if (!appCacheDir.exists()) appCacheDir.mkdirs()
        return appCacheDir.absolutePath
    }
    
    /**
     * ANTI-CACHE PROTECTED PATH RULE: Reroutes preferences outside standard wipe locations
     */
    fun getProfileSharedPrefsPath(profile: ProfileModel, appPackageName: String): String {
        val persistentPrefsDir = File(context.noBackupFilesDir, "profiles/profile_${profile.id}/$appPackageName/shared_prefs")
        if (!persistentPrefsDir.exists()) persistentPrefsDir.mkdirs()
        return persistentPrefsDir.absolutePath
    }

    /**
     * ANTI-CACHE PROTECTED PATH RULE: Reroutes login token databases to survive data clearing
     */
    fun getProfileDatabasesPath(profile: ProfileModel, appPackageName: String): String {
        val persistentDbDir = File(context.noBackupFilesDir, "profiles/profile_${profile.id}/$appPackageName/databases")
        if (!persistentDbDir.exists()) persistentDbDir.mkdirs()
        return persistentDbDir.absolutePath
    }
    
    /**
     * Generates rules explicitly routed to separate volatile components from protected states.
     */
    suspend fun createFsRedirectRule(
        profile: ProfileModel,
        appPackageName: String
    ): Result<FsRedirectRule> = withContext(Dispatchers.IO) {
        try {
            val appDataPath = getProfileAppDataPath(profile, appPackageName)
            val cachePath = getProfileCachePath(profile, appPackageName)
            val prefsPath = getProfileSharedPrefsPath(profile, appPackageName)
            val databasesPath = getProfileDatabasesPath(profile, appPackageName)
            
            Log.d(TAG, "Creating Anti-Cache Persistent FS redirect for: $appPackageName")
            
            val rule = FsRedirectRule(
                profileId = profile.id,
                appPackage = appPackageName,
                originalDataPath = "$basePath/$appPackageName",
                redirectedDataPath = appDataPath,
                cachePath = cachePath,
                sharedPrefsPath = prefsPath,
                databasesPath = databasesPath
            )
            
            Result.success(rule)
        } catch (e: Exception) {
            Log.error(TAG, "Error generating persistent storage mapping rules", e)
            Result.failure(e)
        }
    }
    
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
    val sharedPrefsPath: String,
    val databasesPath: String
)

/**
 * Extension of NewBlackbox's BinderProxyLayer
 * Feeds the custom proxy rules right down into internal client tasks
 */
class BinderProxyExtension(private val fileSystemRedirector: FileSystemRedirector) {
    private val TAG = "BinderProxyExtension"
    
    fun createPackageManagerProxy(profile: ProfileModel): PackageManagerProxy {
        return PackageManagerProxy(profile, fileSystemRedirector)
    }
    
    fun createAccountManagerProxy(profile: ProfileModel): AccountManagerProxy {
        return AccountManagerProxy(profile)
    }
    
    fun createSettingsProxy(profile: ProfileModel): SettingsProxy {
        return SettingsProxy(profile)
    }
}

class PackageManagerProxy(
    private val profile: ProfileModel,
    private val fsRedirector: FileSystemRedirector
) {
    private val TAG = "PackageManagerProxy"
    
    suspend fun getPackageInfo(packageName: String, flags: Int): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "getPackageInfo called for: $packageName")
        null
    }
    
    suspend fun getInstalledPackages(flags: Int): List<String> = withContext(Dispatchers.IO) {
        emptyList()
    }
}

class AccountManagerProxy(private val profile: ProfileModel) {
    private val TAG = "AccountManagerProxy"
    
    suspend fun getAccounts(): List<String> = withContext(Dispatchers.IO) {
        if (profile.googleAccount != null) {
            listOf(profile.googleAccount)
        } else {
            emptyList()
        }
    }
    
    suspend fun getAccountsByType(type: String): List<String> = withContext(Dispatchers.IO) {
        if (type == "com.google" && profile.googleAccount != null) {
            listOf(profile.googleAccount)
        } else {
            emptyList()
        }
    }
}

class SettingsProxy(private val profile: ProfileModel) {
    private val TAG = "SettingsProxy"
    
    suspend fun getSetting(namespace: String, key: String): String? = withContext(Dispatchers.IO) {
        null
    }
}