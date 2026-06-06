# Phase 1 Implementation Architecture

## Overview

Phase 1 establishes the core multi-profile engine with GSF integration, ROM serialization, and lifecycle management.

## Component Breakdown

### 1. **ProfileManager** (`core/profile/ProfileManager.kt`)

- **Responsibility**: Central CRUD operations for profiles
- **Key Methods**:
    - `createProfile()`: Create new profile with unique identifiers
    - `getProfileById()`, `getProfilesForUser()`: Retrieve profiles
    - `updateProfile()`, `deleteProfile()`: Modify profile state
    - `getActiveProfilesForUser()`: Get profiles in specific state
    - `updateGsfToken()`: Store encrypted GSF credentials

- **Unique ID Generation**:
    - Android ID: 16-char hex string (UUID-based)
    - IMEI: 15-digit number
    - MAC Address: Standard format (6 octets)
    - Serial Number: 20-char unique identifier

- **Database**: Room-based ProfileDao with profile entity persistence

---

### 2. **ProfileLifecycleController** (`core/profile/ProfileLifecycleController.kt`)

- **Responsibility**: State machine for profile lifecycle
- **States**:
    - **ACTIVE**: GSF running, apps usable, in RAM
    - **SUSPENDED**: GSF frozen, state checkpointed to ROM, minimal RAM
    - **DORMANT**: Fully serialized to ROM, zero RAM usage

- **State Transitions**:
    - User opens profile → DORMANT/SUSPENDED → ACTIVE (deserialize if needed)
    - User closes profile → ACTIVE → SUSPENDED (checkpoint state)
    - RAM pressure / 24h inactivity → SUSPENDED → DORMANT (full serialization)

- **Auto-Management**:
    - `evaluateProfileStates()`: Runs periodically to auto-suspend/hibernate inactive profiles
    - Inactivity thresholds: 5 min (suspend), 24 hours (hibernate)

---

### 3. **ProfileSerializer** (`core/profile/ProfileSerializer.kt`)

- **Responsibility**: Serialize/deserialize profile state to/from ROM
- **Serialization Format** (`/data/data/com.multispace.app/profiles/profile_{id}/`):
    - `metadata.json`: Profile ID, name, Android ID, GSF ID, serialization timestamp
    - `gsf_state.enc`: Encrypted GSF token, GSF ID, account email
    - `identity.json`: Android ID, IMEI, MAC, Serial, device fingerprint
    - `app_data/`: Per-app storage snapshots
    - `fs_snapshot/`: Filesystem metadata

- **Key Features**:
    - ROM size calculation for quota management
    - ROM integrity verification (checks for critical files)
    - Device fingerprint generation (realistic, per-profile)

---

### 4. **GsfBootstrapper** (`core/gsf/GsfBootstrapper.kt`)

- **Responsibility**: Initialize Google Services Framework for new profile
- **Bootstrap Flow**:
    1. Register unique Android ID with Google
    2. Receive unique GSF ID
    3. Validate GSF health (connectivity, token validity)
    4. Initialize session keepalive mechanism

- **Retry Logic**:
    - Max 3 attempts with exponential backoff (2s, 4s, 6s)
    - Fails if all retries exhausted

- **Data Generated**:
    - GSF ID: 16-char identifier
    - Encrypted token: Base64-encoded (will upgrade to AES-256)

---

### 5. **AccountEnforcer** (`core/gsf/GsfBootstrapper.kt`)

- **Responsibility**: Enforce mandatory Google account on profile creation
- **Flow**:
    1. Block profile activation until Google login completes
    2. Validate account via Google API
    3. Store encrypted credentials
    4. Update profile with account email

---

### 6. **GsfHealthMonitor** (`core/gsf/GsfBootstrapper.kt`)

- **Responsibility**: Monitor and refresh GSF sessions
- **Key Features**:
    - Token refresh interval: 24 hours
    - Automatic refresh before expiration
    - Session validation with Google servers
    - Keepalive heartbeat (prevents session invalidation on idle)

---

### 7. **FileSystemRedirector** (`core/container/FileSystemRedirector.kt`)

- **Responsibility**: Per-profile filesystem isolation (extension of NewBlackbox)
- **Redirection Rules**:
    - `/data/data/{appId}` → `{profile.romPath}/app_data/{appId}`
    - `/data/cache/{appId}` → `{profile.romPath}/cache/{appId}`
    - Shared prefs → `{profile.romPath}/shared_prefs`

- **Binder Proxy Extensions**:
    - PackageManagerProxy: Filter installed apps to profile scope
    - AccountManagerProxy: Return only profile's Google account
    - SettingsProxy: Return profile-specific settings

---

### 8. **Database Layer** (`data/local/`)

- **ProfileEntity**: Room entity with all profile attributes
- **ProfileDao**: Full CRUD + query operations
- **MultiSpaceDatabase**: Room database singleton

---

## Integration Flow: Profile Creation → Activation

```
1. User taps "New Profile"
   ↓
2. ProfileManager.createProfile(request)
   - Generate unique Android ID, IMEI, MAC, Serial
   - Create ROM directory
   - Insert into database
   - Return ProfileModel
   ↓
3. If Google account required:
   AccountEnforcer.enforceGoogleLogin(profile)
   - Launch Google Sign-In
   - Validate account
   - Store encrypted credentials
   ↓
4. GsfBootstrapper.initGsfForProfile(profile, account)
   - Register device with Google (3 retries)
   - Get unique GSF ID
   - Validate GSF health
   - Initialize session keepalive
   ↓
5. ProfileSerializer.serializeToRom(profile)
   - Save metadata, GSF state, identity
   - Create app_data, cache, shared_prefs dirs
   ↓
6. ProfileLifecycleController.activateProfile(profile)
   - Transition state to ACTIVE
   - Profile ready for app installation
```

---

## Integration Flow: Profile Suspension → Hibernation

```
1. User closes profile app
   ↓
2. ProfileLifecycleController.suspendProfile(profileId)
   - Checkpoint state to ROM (GSF tokens, account, app state)
   - Transition to SUSPENDED
   - Free RAM resources
   ↓
3. (Optional) After 24h inactivity:
   ProfileLifecycleController.hibernateProfile(profileId)
   - Full serialization to ROM
   - Transition to DORMANT
   - Zero RAM usage
   ↓
4. User reopens profile:
   ProfileLifecycleController.activateProfile(profileId)
   - Deserialize from ROM if DORMANT
   - Restore GSF tokens, account state
   - Verify GSF health (refresh token if needed)
   - Transition to ACTIVE
```

---

## Data Isolation Guarantees

### File System Isolation

- Each app's data stored in profile-specific directory
- Filesystem redirector enforces at NDK level (extends NewBlackbox's hooks)
- No cross-profile access possible

### GSF/Account Isolation

- Each profile has unique Android ID, IMEI, MAC, Serial
- Each profile has unique GSF ID
- Account manager proxy returns only profile's account
- Package manager proxy returns only profile-installed apps

### Memory Isolation

- SUSPENDED/DORMANT profiles consume minimal RAM
- Active profiles isolated via existing BlackBox process isolation
- No shared memory between profiles

---

## ROM Storage Structure

```
/data/data/com.multispace.app/profiles/
├── profile_1/
│   ├── metadata.json           # Profile metadata
│   ├── gsf_state.enc           # Encrypted GSF token
│   ├── identity.json           # Device identity
│   ├── app_data/               # Per-app data snapshots
│   │   ├── com.instagram/
│   │   ├── com.facebook/
│   │   └── ...
│   ├── cache/                  # Per-app cache
│   │   ├── com.instagram/
│   │   └── ...
│   ├── shared_prefs/           # Shared preferences
│   └── fs_snapshot/            # Filesystem metadata
│
├── profile_2/
│   └── ...
│
└── profile_N/
    └── ...
```

---

## Key Design Decisions

### 1. **Pico GSF Model**

- Minimal Google Play Services set (GSF, Account Manager, minimal Play Services)
- Reduces initialization time vs full GApps
- Sufficient for app auth and Play Store functionality

### 2. **Profile State Machine**

- Three states allow fine-grained control over RAM/storage tradeoffs
- Auto-management reduces user burden
- Fast resume from SUSPENDED (tokens cached)
- Very fast resume from DORMANT (full deserialization)

### 3. **Encrypted Credentials**

- GSF tokens and account credentials encrypted in ROM
- Future upgrade: AES-256 (currently Base64 for MVP)
- Keys can be derived from profile/device identity

### 4. **Unique Identifiers**

- Android ID, IMEI, MAC, Serial all unique per profile
- Prevents detection as multi-profile container
- Allows apps to distinguish between profiles naturally

### 5. **Deferred Features** (v1.1+)

- Profile encryption (full AES-256)
- Custom device identity (advanced anti-detection)
- Profile export/import
- Premium analytics

---

## Testing Strategy

### Unit Tests

- Profile CRUD operations
- ID generation uniqueness
- State transition logic
- Serialization/deserialization

### Integration Tests

- Multi-profile creation with independent GSF
- Profile suspend/resume cycle
- GSF bootstrap with retry
- Concurrent profile activation
- Cross-profile data isolation verification

### End-to-End Tests

- Profile creation through activation
- App installation per profile
- GSF token refresh
- Long-term stability (24h soak test)

---

## Success Metrics (Phase 1 Completion)

- [ ] 2 profiles can be created independently
- [ ] Each profile has unique Android ID + GSF ID
- [ ] Profiles can transition through ACTIVE → SUSPENDED → DORMANT
- [ ] Profile resume from DORMANT completes in <3 seconds
- [ ] GSF health check passes for both profiles
- [ ] No cross-profile data visible in filesystem
- [ ] ROM checkpoint/restore cycle completes <2 seconds per profile
- [ ] Integration tests pass on API 24, 30, 34
