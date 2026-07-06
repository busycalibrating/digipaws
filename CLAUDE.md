# Code Style
When working with this codebase, prioritize readability over cleverness. Ask clarifying questions before making architectural changes. 
Put comments only when absolutely necessary or documenting. It only has a single module :app

# About this project
Curbox is an advanced screentime management tool for android that utilizes accessibility services to work. 

# Architecture
The app follows a compartmentalization style. There is a single accessibility service, AppBlockerService (declared as a separate android process), which hosts both:
1) Usage tracking features (app usage stats, website usage, reel counting)
2) Blocking features that perform actions like home press, back press etc

Each feature (like app blocking, app usage stats, keyword blocking) is a compartmental class with a service object. 
This class obj is created in the accessibility service, and the service instance is passed to the feature in the onServiceConnected() method.
All function responsible for setting up the feature(like loading configs), broadcast receiver are aswell declared in this method itself.

The service runs low memory features (like app blocking) and the trackers in onAccessibilityEvent itself while high memory consuming tasks (reelblocking, Ui hider) 
that traverse the entire ui node, run in a background worker that is fed with event updates continuously.
Hardcoded viewids for blocking are always stored separately in hardcoded folder.

Blocking features are declared in the blockers folder, tracking features in the trackers folder. Both run inside AppBlockerService.

The app should ensure no matter what happens the AppBlockerService never crashes. Room uses multi instance invalidation and DataStore uses MultiProcessDataStoreFactory because the service process and the UI process share them.

# Build variants
Three product flavors in the "version" dimension (app/build.gradle.kts):
- **full**: every feature, including cross device sync.
- **playstore**: sync, but no UI hider and no anti uninstall. Its manifest (app/src/playstore/AndroidManifest.xml) strips the AdminReceiver (device admin) and NodePickerService with tools:node="remove", so the build never holds those capabilities. Sync is free here, same as the full build. SyncEntitlement is a free stub shared from src/sync, so no build ever compiles Play Billing.
- **fdroid**: every feature except sync. No INTERNET permission, no Firebase, no Supabase code compiled in.

Feature availability is gated by BuildConfig flags: SUPPORTS_UI_HIDER and SUPPORTS_ANTI_UNINSTALL (true by default, false in playstore). FDROID_VARIANT gates sync UI. Check these flags before surfacing either feature in UI, services, or the Curbox API.

Sync code lives in app/src/sync/java and is added as an extra source dir to the full and playstore flavors only. app/src/fdroid/java holds the offline stub (SyncProviderFactory). CI release workflows build the fdroid flavor.

# Features
## App blocker Service:
Folder app/src/main/java/neth/iecal/curbox/blockers
├── AntiUninstallBlocker.kt
├── AppBlocker.kt
├── BaseBlocker.kt
├── BrowserBlocker.kt
├── FocusModeBlocker.kt
├── KeywordBlocker.kt
├── ReelBlocker.kt
└── uihider (is a folder)

- AppBlocking
- Reel blocking(AppBlocker.kt) :block instagram reels, youtube shorts, facebook reels)
- Keyword Blocking: block websites, regex, keyword. This service is not responsible for searching keywords on screen, but only analyze the live updates fed by 
WebsiteUsageTracker and decide whether a block is needed or not
- Focus Mode Blocker: Temporarily blocks apps and keywords for a set duration(like 5 mins) so user can focus on a task like studying.
- View Blocker: Uses an overlay to hide specific ui elements or areas on screen when they're opened.
- Anti Uninstall (full and fdroid only): Uses device admin (receivers/AdminReceiver) so the user can't impulsively uninstall the app. Shared logic lives in utils/AntiUninstallManager.kt.

## Usage tracking (runs inside AppBlockerService):
Folder: app/src/main/java/neth/iecal/curbox/trackers
├── AppUsageTracker.kt
├── ReelsCountTracker.kt
└── WebsiteUsageTracker.kt

- AppUsageTracker: Tracks and stores app usage analytics in a room db
- ReelsCountTracker: Counts the number of short-form video content you scroll.
- WebsiteUsageTracker: Tracks and stores website analytics in a room db

# Storing data and models
The app/src/main/java/neth/iecal/curbox/data/models 
folder stores raw data classes and models

The app/src/main/java/neth/iecal/curbox/data/db
folder pools together all db releated objects including their data class

The project uses room database to store large info
while datastore for stuff like configuration.

# UI
- Android dynamic colors
- Uses a combination of ascii art, typography, minimalist and material ui.
- Use default values mostly for text colors, button colors etc

## Fonts
- Coolvetica: Used for extreme typographical and hooky screens like onboarding
- Inter : Used for most of the app


# UX
- Doesn't overwhelm users
- calming and peaceful
- smooth animations between views resizing, disappearing, appearing etc

## Writing user displayed text
- Never use dashes(-) anywhere
- Keep simple language at a 6th grader level
- Speak in first person
- Be crisp and concise
- Explain with real world examples if too complex
- The reader is not tech savy

# Overall Java Code Structure
Main working directory: app/src
├── main
│   ├── java
│   │   └── neth
│   │       └── iecal
│   │           └── curbox
│   │               ├── anti_stimulants
│   │               ├── api (Curbox API other apps can bind to, Shizuku style)
│   │               ├── blockers
│   │               │   └── uihider
│   │               ├── data
│   │               │   ├── db
│   │               │   └── models
│   │               ├── hardcoded
│   │               ├── receivers
│   │               ├── services
│   │               ├── trackers
│   │               ├── ui
│   │               │   ├── activity
│   │               │   ├── fragments
│   │               │   │   ├── installation
│   │               │   │   │   └── onboarding
│   │               │   │   │       └── screens
│   │               │   │   └── main
│   │               │   │       ├── focus
│   │               │   │       ├── reducers
│   │               │   │       │   ├── advanced (anti uninstall, service protection)
│   │               │   │       │   ├── analytics
│   │               │   │       │   ├── anti_stimulants
│   │               │   │       │   │   ├── grayscale
│   │               │   │       │   │   ├── mindful_messages
│   │               │   │       │   │   └── reel_counter
│   │               │   │       │   ├── api
│   │               │   │       │   ├── blockertools
│   │               │   │       │   │   ├── appBlocker
│   │               │   │       │   │   ├── autodnd
│   │               │   │       │   │   ├── keywordBlocker
│   │               │   │       │   │   ├── reelBlocker
│   │               │   │       │   │   ├── shared
│   │               │   │       │   │   └── uiHider
│   │               │   │       │   └── sync
│   │               │   │       └── usage
│   │               │   ├── overlay
│   │               │   ├── views
│   │               │   └── widgets
│   │               ├── utils
│   │               └── views
│   └── res
│       ├── anim
│       ├── drawable
│       ├── font
│       ├── layout
│       ├── menu
│       ├── mipmap-anydpi-v26
│       ├── values
│       ├── values-night
│       └── xml
├── sync (cross device sync code, compiled into full and playstore only)
├── full (manifest with sync permissions and FCM service)
├── playstore (manifest with sync permissions, strips device admin and NodePickerService)
└── fdroid (offline SyncProviderFactory stub, empty manifest)
