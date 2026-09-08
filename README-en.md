#Video Collection Player (Android)
#This software is assisted by AI

##Technology Stack

- Kotlin + Jetpack Compose（Material 3）
-Media3 ExoPlayer (video playback)
-Room (Data Persistence)
-Coil (cover image loading)
-SAF (Storage Access Framework, Folder Import)

##Functional comparison

|Desktop Features | Android Implementation|
|-----------|--------------|
|Import folder to generate video collection | SAF Select directory → 'DocumentFile' Scan videos|
|Video Collection Cover (cover. png/first frame) | MediaMetadata Retriever Extract first frame or read cover. png|
|Group Management (New/Rename/Delete/Move) | Room+Homepage Group Filtering and Long Press Menu|
|Playlist, Previous/Next Episode | Bottom Pop up Layer of Playpage+Control Bar|
|Play progress memory (continuation) | ` videos. last_position `+card "continue watching" corner marker|
|Volume, Full Screen | ExoPlayer Volume+System Bar Hidden|
|Dark/Light Theme | Material3 Follow System|

##Build

1. Open this directory using Android Studio (Koala or later)
2. Wait for Gradle synchronization (dependencies will automatically download)
3. Connect the device or start the emulator (Android 8.0+, API 26+)
4. Click on Run ▶

You can also build on the command line:

```
gradlew assembleDebug
```

The product is located in ` app/build/outputs/apk/debug/`.
##Instructions for use

###Media library directory (recommended, experience the same as the computer version)

The first startup will guide you to set up a * * media library root directory * *, and then place videos in the following hierarchy:

```
Media Library Root Directory/
∝ - TV drama A/← automatically becomes a video collection
│∝ - cover. png ← Optional, as a cover
│∝ - Episode 01. mp4
│└ - Episode 02.mp4
∝ - Documentary B/← Another video collection
│   └── . ..
└ - Randomly shoot. mp4 ← Videos scattered in the root directory are classified as' unorganized '
```

After setting, * * automatic scanning * * can be started each time, or you can click the refresh button in the upper right corner to manually scan; New/deleted/renamed files will be synchronized.

Two modes:

-* * Authorized directory mode (recommended) * *: Authorize once with the system file selector, without any permission, and can be listed on Play.
-Fixed path mode: Simply use the 'Movies/Video Collection Player' directory and copy the video into the file manager/USB, which is closest to the computer version. Requires' All File Access' permission (redirected to the system settings page to enable), which Google Play restricts for personal use or sideloading.

The folder icon in the upper right corner allows you to change directories, switch modes, and rescan at any time.

###Import video

Click on the 'Download' icon in the upper right corner, there are two ways to import:

1. * * Import video files/compressed files * *: Multiple selections can be made at once
-Video file: Import directly as a video collection (named after the file name)
-Compressed file (zip/rar): automatically decompressed, only extracting the video from it
-After selection, a naming confirmation box will pop up, which automatically fills in the extracted file names. You can modify them one by one and confirm
2. * * Import Folder * *: Convert existing directories into a video collection as a whole

Regardless of the method, videos will be automatically classified and stored according to the hierarchy of * * media library root directory/video collection name/video file * *, and will be automatically scanned and stored after import.
###Other operations

1. You can also choose not to set a media library directory and click "Import Folder" in the bottom right corner to import a specific directory separately
2. Supports common formats such as mp4/mkv/avi/mov/wmv/flv/webm/ts, etc
If there is a 'cover. png' (or poster/folder/thumbnail) in the directory, it will be used as the cover. Otherwise, the first frame of the first video will be automatically captured
4. Click on the card to enter the playback mode; Long press the card to rename/move groups/delete
5. Click on the center of the screen to hide/display the control bar on the playback page; Automatically play the next episode after completion of playback

##Data storage

-Media Library: Application Private Database (Room)
-Cover: Application Private Directory ` files/covers/`
-The video file itself is not copied, and the original location is read through SAF authorization
"# Video-player"
