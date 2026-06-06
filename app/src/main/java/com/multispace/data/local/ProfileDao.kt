package com.multispace.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long
    
    @Update
    suspend fun updateProfile(profile: ProfileEntity)
    
    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)
    
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfileById(profileId: Int): ProfileEntity?
    
    @Query("SELECT * FROM profiles WHERE userId = :userId ORDER BY createdAt DESC")
    fun getProfilesByUser(userId: Int): Flow<List<ProfileEntity>>
    
    @Query("SELECT * FROM profiles WHERE state = :state")
    suspend fun getProfilesByState(state: String): List<ProfileEntity>
    
    @Query("SELECT COUNT(*) FROM profiles WHERE userId = :userId")
    suspend fun getProfileCountForUser(userId: Int): Int
    
    @Query("SELECT * FROM profiles WHERE userId = :userId AND state = :state")
    suspend fun getActiveProfilesForUser(userId: Int, state: String): List<ProfileEntity>
    
    @Query("UPDATE profiles SET state = :newState, lastAccessedAt = :timestamp WHERE id = :profileId")
    suspend fun updateProfileState(profileId: Int, newState: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM profiles WHERE userId = :userId")
    suspend fun deleteAllProfilesForUser(userId: Int)
    
    @Query("SELECT * FROM profiles ORDER BY lastAccessedAt DESC LIMIT 1")
    suspend fun getLastAccessedProfile(): ProfileEntity?
    
    @Query("SELECT * FROM profiles WHERE androidId = :androidId")
    suspend fun getProfileByAndroidId(androidId: String): ProfileEntity?
}
