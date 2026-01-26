# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean

# Check for dependency updates
./gradlew dependencies
```

## Architecture

MVVM + Repository pattern with Jetpack Compose.

```
UI (Compose Screens) → ViewModel (StateFlow) → Repository → Data Sources
                                                    ↓
                                    ┌───────────────┴───────────────┐
                                    │                               │
                               Room Database                  Google Tasks API
                            (DailyCompletion,                 (TasksRepository)
                             CompletedTask)
```

### Key Layers

- **UI Layer**: `ui/screens/` - Compose screens observe ViewModel StateFlow
- **ViewModel Layer**: `viewmodel/` - AndroidViewModel with StateFlow for state management
- **Repository Layer**: `data/repository/`, `api/` - Business logic and API calls
- **Data Layer**: `data/dao/`, `data/entity/` - Room database with DAOs

### Core Services

- **TaskCounterService**: Foreground service that periodically (5min) syncs with Google Tasks API, updates system overlay, and records task completions
- **TaskCounterOverlay**: System overlay widget with animations (tap, drag, idle breathing, completion celebration)
- **GoogleAuthManager**: OAuth2 authentication with TASKS_READONLY scope
- **SyncManager**: Syncs completed task history from Google Tasks to local Room DB

### Database Schema (Room v2)

- `daily_completions`: date (PK), completedCount, updatedAt
- `completed_tasks`: id (PK), taskId, title, date, completedAt

### Navigation

Bottom bar with 3 destinations: Home (task count) / Dashboard (stats, heatmap) / Settings (auth, sync)

## Tech Stack

- Kotlin 1.9.22, Java 17, Target SDK 34, Min SDK 26
- Jetpack Compose with Material3
- Room 2.6.1 with KSP
- Google Tasks API via Play Services
- Coil for image loading

## Required Permissions

SYSTEM_ALERT_WINDOW (overlay), INTERNET, VIBRATE, RECEIVE_BOOT_COMPLETED, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
