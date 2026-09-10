[**简体中文**](./README.md)|[**English**](./README-en.md)
# Video Set Player (Android)
#This software was developed with AI assistance

**Current version: 1.1.0** (versionCode 2)

## Tech Stack

- Kotlin + Jetpack Compose (Material 3)
- Media3 ExoPlayer (video playback)
- Room (data persistence)
- Coil (cover image loading)
- SAF (Storage Access Framework, directory authorization and import)
- junrar (RAR extraction; ZIP uses the built-in `java.util.zip`)

## Feature Comparison

| Desktop Version Feature | Android Version Implementation |
|-------------------------|--------------------------------|
| Import a folder to create a video set | SAF picks a directory → `DocumentFile` scans for videos |
| Video set cover (cover.png / first frame) | `MediaMetadataRetriever` extracts the first frame or reads cover.png |
| Group management (create / rename / delete / move) | Groups appear as folder cards; tap to **navigate in**, long-press to rename / delete |
| Group cover | Choose from the cover of any video set inside the group |
| Playlist, previous episode / next episode | Bottom sheet on the player page + control bar |
| Playback progress memory (resume) | `videos.last_position`, saved on exit / episode switch / periodically |
| Watch progress visualization | "Watched N/M" badge + progress bar on video set covers; per-episode watched / unwatched state and progress bar in the playlist |
| Search | Top bar search across video set names and video file names |
| Playback control | Play/pause, seek, previous/next episode, speed, fullscreen |
| Fast-forward | Long-press for 2× speed (bottom area in portrait / right side in landscape) |
| Fullscreen | Forces landscape (works even when system auto-rotate is off) |
| Light / dark theme | Material 3 follows the system |
| Volume | Controlled by the phone's hardware volume keys |

## Build

1. Open this directory with Android Studio (Koala or newer)
2. Wait for the Gradle sync (dependencies are downloaded automatically)
3. Connect a device or start an emulator (Android 8.0+, API 26+)
4. Click Run ▶

Building from the command line:

```
gradlew assembleDebug     :: debug build
gradlew assembleRelease   :: signed release build
```

The output is located in `app/build/outputs/apk/`.

> Command-line builds require `JAVA_HOME` to point to JDK 17 or newer (the `jbr` bundled with Android Studio works).
> The release signature is read from `keystore.properties` in the project root (this file and `*.jks` are listed in `.gitignore` — do not commit them).

## Usage

### Media Library Directory (recommended)

On first launch the app asks **whether to create a folder for storing videos**, or you can pick an existing directory. Afterwards, place your videos in the following structure:

```
Media library root/
├── Series A/             ← automatically becomes a video set
│   ├── cover.png         ← optional, used as the cover
│   ├── Episode 01.mp4
│   └── Episode 02.mp4
├── Documentary B/        ← another video set
│   └── ...
└── random clip.mp4       ← loose videos in the root go into "Unsorted"
```

After setup, the app **auto-scans on every launch**; you can also tap the refresh button at the top right to scan manually. Added / deleted / renamed files are all synced.

Two modes:

- **Authorized directory mode (recommended)**: authorize once with the system file picker, requires no permissions, and is Play Store ready.
- **Fixed path mode**: directly uses the `Movies/Video Set Player` directory. Copy videos into it with a file manager / USB — the closest to the PC version. It requires the "All files access" permission (jump to the system settings page to enable it). Google Play restricts this permission; self-use or sideloading is unaffected.

The "folder icon" at the top right lets you change the directory, switch modes, or rescan at any time.

### Importing Videos

Tap the "download" icon at the top right for two import options:

1. **Import video files / archives**: supports multi-select
    - Video files: imported directly as a video set (named after the file name)
    - Archives (zip / rar): extracted automatically, only the videos inside are kept
    - A confirmation dialog appears with names pre-filled from the file names; you can edit each one
2. **Import a folder**: adds an existing directory as a whole video set

**Smart merging**: when importing several files, common parts are detected and a merge is suggested, for example

```
GuguGaga 1-7.zip  ┐
GuguGaga 8-10.zip ┘ → suggested merge: "GuguGaga"
```

- Episode suffixes such as "1-7" or "EP01" are stripped before comparison
- Explicit separators such as "Season 1", "Part 2", "Movie", "OVA", "Special" are recognized and are **not** merged
- Merged entries show their source files in the dialog; you can rename them or tap the button to **split** them back into separate imports

Regardless of the method, videos are automatically organized and stored in the **Media library root / video set name / video file** structure, and are automatically scanned into the library once the import completes.

### Groups and Video Sets

- Root level of the home page: groups appear as **folder cards**, mixed with ungrouped video sets
- Tap a folder to **enter the group**; a "← Back to all / group name" breadcrumb is shown at the top
- **Long-press a group**: open / set cover / rename / delete
- **Long-press a video set**: play / rename / move to group / delete
- When setting a group cover you can choose the cover of **any video set inside the group**, or clear it

### Search

Tap 🔍 in the top bar to enter search mode:

- **Video sets**: filtered in real time by name (including sets inside groups)
- **Videos**: fuzzy match on file names (e.g. type "EP05")
- Tapping a video result **jumps straight to playing that episode**

### Watch Progress

- Video set cards show "Watched N/M" at the bottom right, with an overall progress bar along the cover; fully watched shows "✓ Completed"
- Each item in the playlist shows a state icon and a per-episode progress bar: now playing / completed / watched to mm:ss / unwatched
- Missing durations are filled in automatically when the playlist is opened, so percentages are accurate
- Progress is saved on exit, on episode switch, and periodically (every 5 seconds) during playback; playback resumes next time (fragments shorter than 3 seconds are ignored)

### Player Controls

| Action | Description |
|---|---|
| Tap the center of the screen | Show / hide the control bar |
| Long-press (bottom quarter in portrait / right quarter in landscape) | Play at 2× speed; release to restore. A `»2×` hint appears at the top |
| Drag the progress bar | Seek |
| ⏮ / ⏯ / ⏭ | Previous episode / play-pause / next episode |
| List button | Open the playlist (with per-episode progress) |
| Fullscreen button | Enter fullscreen and **force landscape** (portrait videos stay portrait), independent of system auto-rotate |
| Back button (in fullscreen) | Exits fullscreen instead of going back |

The next episode plays automatically when one finishes; unsupported or broken videos show a clear error message with an option to skip to the next episode.

## Data Storage

- Media library: app-private database (Room, `video_player.db`)
- Covers: app-private directory `files/covers/` (JPEG)
- Video files are not duplicated (imported files are copied into the media library directory); they are read via SAF authorization or file paths

## Permissions

| Permission | Purpose | Notes |
|---|---|---|
| None (authorized directory mode) | Read the directory the user selected | Recommended, requires no permissions |
| `MANAGE_EXTERNAL_STORAGE` | "Fixed path mode" reads and writes `/sdcard` directly | Optional; Google Play reviews this strictly for regular apps, self-use / sideloading is unaffected |

## Known Limitations

- Archives: only **zip / rar** are supported (7z is not)
- Directory scanning is **single-level**: subdirectories of the media library root are video sets; deeper levels are not recursed
- If folder creation fails during import (not authorized), the app falls back to its private directory
