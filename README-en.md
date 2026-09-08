# Video Set Player (Android)

An Android app ported from the features of the desktop version of *Video Player* (PyQt6).

## Tech Stack

- Kotlin + Jetpack Compose (Material 3)
- Media3 ExoPlayer (video playback)
- Room (data persistence)
- Coil (cover image loading)
- SAF (Storage Access Framework, folder import)

## Feature Comparison

| Desktop Version Feature | Android Version Implementation |
|-------------------------|--------------------------------|
| Import a folder to create a video set | SAF picks a directory → `DocumentFile` scans for videos |
| Video set cover (cover.png / first frame) | `MediaMetadataRetriever` extracts the first frame or reads cover.png |
| Group management (create / rename / delete / move) | Room + homepage group filtering and long-press menu |
| Playlist, previous episode / next episode | Bottom sheet on the player page + control bar |
| Playback progress memory (resume) | `videos.last_position` + "Continue Watching" badge on cards |
| Volume, fullscreen | ExoPlayer volume + system bar hiding |
| Light / dark theme | Material 3 follows the system |

## Build

1. Open this directory with Android Studio (Koala or newer)
2. Wait for the Gradle sync (dependencies are downloaded automatically)
3. Connect a device or start an emulator (Android 8.0+, API 26+)
4. Click Run ▶

You can also build from the command line:

```
gradlew assembleDebug
```

The output is located in `app/build/outputs/apk/debug/`.

## Usage

### Media Library Directory (recommended, matches the PC experience)

On first launch you will be guided to set up a **media library root directory**. Afterwards, place your videos in the following structure:

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
   - After selecting, a naming confirmation dialog appears with the extracted file name pre-filled; you can edit each one and confirm
2. **Import a folder**: adds an existing directory as a whole video set

Regardless of the method, videos are automatically organized and stored in the **Media library root / video set name / video file** structure, and are automatically scanned into the library once the import completes.

### Other Actions

1. You may also skip setting up a media library directory and import a single directory via the "Import Folder" button at the bottom right
2. Common formats are supported: mp4 / mkv / avi / mov / wmv / flv / webm / ts, etc.
3. If the directory contains a `cover.png` (or poster / folder / thumbnail), it is used as the cover; otherwise the first frame of the first video is captured automatically
4. Tap a card to play; long-press a card to rename / move group / delete
5. On the player page, tap the center of the screen to show / hide the control bar; when playback finishes, the next episode plays automatically

## Data Storage

- Media library: app-private database (Room)
- Covers: app-private directory `files/covers/`
- Video files themselves are not copied; they are read from their original location via SAF authorization
