package com.multispace

import android.app.Application
import android.util.Log
import com.multispace.core.container.FileSystemRedirector
import com.multispace.core.gsf.AccountEnforcer
import com.multispace.core.gsf.GsfBootstrapper
import com.multispace.core.gsf.GsfHealthMonitor
import com.multispace.core.profile.ProfileLifecycleController
import com.multispace.core.profile.ProfileManager
import com.multispace.core.profile.ProfileSerializer
import com.multispace.data.local.MultiSpaceDatabase
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Main application class for MultiSpace
 * Initializes all core managers and managers on app startup
 */
class MultiSpaceApplication : Application() {
    private val TAG = "MultiSpaceApplication"
    
    companion object {
        @Volatile
        private var instance: MultiSpaceApplication? = null
        
        fun getInstance(): MultiSpaceApplication {
            return instance ?: throw RuntimeException("MultiSpaceApplication not initialized")
        }
    }
    
    // Core managers (lazy initialized)
    private lateinit var database: MultiSpaceDatabase
    private lateinit var profileManager: ProfileManager
    private lateinit var profileLifecycleController: ProfileLifecycleController
    private lateinit var profileSerializer: ProfileSerializer
    private lateinit var fsRedirector: FileSystemRedirector
    private lateinit var gsfBootstrapper: GsfBootstrapper
    private lateinit var accountEnforcer: AccountEnforcer
    private lateinit var gsfHealthMonitor: GsfHealthMonitor
    private lateinit var gson: Gson
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.d(TAG, "MultiSpaceApplication initializing...")
        
        try {
            // Initialize Gson
            gson = GsonBuilder()
                .setPrettyPrinting()
                .create()
            Log.d(TAG, "Gson initialized")
            
            // Initialize database
            database = MultiSpaceDatabase.getInstance(this)
            Log.d(TAG, "Database initialized")
            
            // Initialize profile manager
            profileManager = ProfileManager(this, database)
            Log.d(TAG, "ProfileManager initialized")
            
            // Initialize profile lifecycle controller
            profileLifecycleController = ProfileLifecycleController(this, profileManager)
            Log.d(TAG, "ProfileLifecycleController initialized")
            
            // Initialize profile serializer
            profileSerializer = ProfileSerializer(this, gson)
            Log.d(TAG, "ProfileSerializer initialized")
            
            // Initialize filesystem redirector
            fsRedirector = FileSystemRedirector()
            Log.d(TAG, "FileSystemRedirector initialized")
            
            // Initialize GSF layer
            gsfBootstrapper = GsfBootstrapper(this)
            accountEnforcer = AccountEnforcer(this)
            gsfHealthMonitor = GsfHealthMonitor(this)
            Log.d(TAG, "GSF layer initialized")
            
            Log.i(TAG, "MultiSpaceApplication initialization complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MultiSpaceApplication", e)
            throw RuntimeException("Failed to initialize MultiSpaceApplication", e)
        }
    }
    
    // Getters for managers
    fun getDatabase(): MultiSpaceDatabase = database
    fun getProfileManager(): ProfileManager = profileManager
    fun getProfileLifecycleController(): ProfileLifecycleController = profileLifecycleController
    fun getProfileSerializer(): ProfileSerializer = profileSerializer
    fun getFileSystemRedirector(): FileSystemRedirector = fsRedirector
    fun getGsfBootstrapper(): GsfBootstrapper = gsfBootstrapper
    fun getAccountEnforcer(): AccountEnforcer = accountEnforcer
    fun getGsfHealthMonitor(): GsfHealthMonitor = gsfHealthMonitor
    fun getGson(): Gson = gson
    
    override fun onTerminate() {
        super.onTerminate()
        // Clean up resources
        profileManager.close()
        Log.d(TAG, "MultiSpaceApplication terminated")
    }
}
