package com.multispace.core.profile

import com.multispace.data.local.ProfileState
import java.util.*

data class ProfileModel(
    val id: Int = 0,
    val userId: Int,
    val profileName: String,
    val googleAccount: String? = null,
    val androidId: String,
    val gsfId: String? = null,
    val imei: String,
    val macAddress: String,
    val serialNumber: String,
    val gsfTokenEncrypted: String? = null,
    val accountTokenEncrypted: String? = null,
    val state: ProfileState = ProfileState.ACTIVE,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val romPath: String,
    val isEncrypted: Boolean = false,
    val encryptionKeyHash: String? = null
) {
    val isActive: Boolean get() = state == ProfileState.ACTIVE
    val isSuspended: Boolean get() = state == ProfileState.SUSPENDED
    val isDormant: Boolean get() = state == ProfileState.DORMANT
}

data class CreateProfileRequest(
    val userId: Int,
    val profileName: String,
    val googleAccount: String? = null,
    val isEncrypted: Boolean = false
)
