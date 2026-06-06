package com.multispace.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val userId: Int,
    val profileName: String,
    val googleAccount: String?,
    val androidId: String,
    val gsfId: String?,
    
    // Device identity - persisted per profile
    val imei: String,
    val macAddress: String,
    val serialNumber: String,
    
    // GSF & Authentication state
    val gsfTokenEncrypted: String?,
    val accountTokenEncrypted: String?,
    
    // Profile lifecycle state
    val state: String, // ACTIVE, SUSPENDED, DORMANT
    val lastAccessedAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    
    // ROM serialization path
    val romPath: String,
    
    // Feature flags
    val isEncrypted: Boolean = false,
    val encryptionKeyHash: String? = null
)

enum class ProfileState {
    ACTIVE,      // GSF running, apps usable, in RAM
    SUSPENDED,   // GSF frozen, state on ROM, minimal RAM
    DORMANT      // Fully serialized to ROM, zero RAM usage
}
