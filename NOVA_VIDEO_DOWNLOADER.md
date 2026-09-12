# Nova Video Downloader (feature branch only)

This branch (`feature/video-downloader`) adds a built-in **video/audio downloader**
to Nova Browser. It is intentionally self-contained so it can be removed by simply
deleting this branch — nothing on `main` or in the release workflow is touched.

## What it does

A third bundled WebExtension (`nova-video@nova.browser`) that:

- watches network traffic and detects media the page loads — direct files
  (`Content-Type: video/*` / `audio/*`, or a known extension) and streaming
  manifests (HLS `.m3u8`, DASH `.mpd`);
- shows **one small (38px) download-arrow icon**, and only while the page actually
  has a downloadable video/audio. There is no other on-screen chrome: no floating
  panel, no "Rescan" button, no "Hide here" dialog. The icon is draggable and hides
  itself while a video is fullscreen;
- tapping the icon opens a compact picker (at most 40% of the screen height) listing
  the detected items, with a 32px `X` to close it and tap-outside-to-close;
- for HLS it reads the master playlist and offers every quality;
- for DASH it lists the video/audio representations;
- downloads by assembling the bytes in the page and handing them to the normal
  Fenix download flow (blob download), so the standard download notification and
  Downloads list are used;
- on sites whose media cannot be reached as a plain URL (YouTube, Vimeo,
  Dailymotion, Facebook, Instagram, X, TikTok) it offers a **site entry** that
  hands the page URL to a **bundled `yt-dlp`** running natively, which extracts
  the video itself. An "Audio only" option extracts just the audio (MP3).

It also reports `<video>`/`<audio>` elements found in the page (including frames).

## On / off switch in the 3-dot menu

The whole feature can be switched off without uninstalling anything:

- The browser's 3-dot menu has a **"Video Downloader"** item with an on/off switch
  (same pattern as the existing "Allow background playback" switch).
- Turning it **on** enables the bundled extension in the engine (`EnableSource.USER`),
  so the small download icon appears on pages that have a video.
- Turning it **off** disables the extension: the content script stops running and
  removes the icon/panel on the page it is already loaded in (it notices the dead
  extension context and tears itself down within a few seconds). Nothing is shown
  while browsing.
- The choice is stored in shared preferences (`NovaVideoDownloader` /
  `novaVideoDownloaderEnabled`, default on) and re-applied on launch, so it
  survives restarts.

## Files added / changed

| File | Change |
| --- | --- |
| `app/src/main/assets/extensions/nova-video/manifest.json` | new — MV2 manifest of the bundled extension (has the `nativeMessaging` permission) |
| `app/src/main/assets/extensions/nova-video/background.js` | new — network sniffing, playlist/manifest parsing, binary fetch fallback, yt-dlp bridge |
| `app/src/main/assets/extensions/nova-video/content.js` | new — icon-only UI + downloader picker (shadow DOM), incl. the yt-dlp site entry |
| `app/src/main/java/org/mozilla/fenix/components/NovaVideoDownloader.kt` | new — preference + engine enable/disable helper |
| `app/src/main/java/org/mozilla/fenix/components/NovaYtDlp.kt` | new — native yt-dlp bridge (init, downloads, progress, MediaStore publish) |
| `app/build.gradle` | adds the `youtubedl-android` `library` + `ffmpeg` dependencies |
| `app/proguard-rules.pro` | keep/dontwarn rules for youtubedl-android, Jackson and commons-io |
| `app/src/main/java/org/mozilla/fenix/FenixApplication.kt` | `NOVA_VIDEO_ADDON_ID` constant + `installBuiltInWebExtension(...)` call + re-applies the stored on/off preference |
| `app/src/main/java/org/mozilla/fenix/components/menu/compose/MainMenu.kt` | new "Video Downloader" menu item with a switch |
| `app/src/main/java/org/mozilla/fenix/components/menu/MenuDialogFragment.kt` | wires the switch state + toggles the extension |
| `app/src/main/res/values/strings.xml` | `browser_menu_video_downloader` (+ `_on` / `_off`) strings |
| `.github/workflows/build-video-downloader.yml` | new — builds and signs the APK and uploads it as a **workflow artifact only** (no GitHub Release) |
| `NOVA_VIDEO_DOWNLOADER.md` | new — this file |

## How to build / test

Push to `feature/video-downloader` (or run the workflow manually). The
`Build Nova Video Downloader (feature branch)` workflow builds
`app:assembleForkRelease`, signs it with the same repo secrets as the main
build, and uploads **`nova-video-downloader.apk`** as a workflow artifact.
It deliberately has `permissions: contents: read` and no `gh release` steps, so
it can never publish to the main release.

Download the artifact from the Actions run page and sideload it to test.

## How to remove the feature

1. Delete the branch (this deletes every file listed above, including the
   workflow, so no build runs anymore), **or**
2. Revert the commit and remove the `nova-video` assets, the `NovaVideoDownloader`
   and `NovaYtDlp` helpers, the `NOVA_VIDEO_ADDON_ID` constant +
   `installBuiltInWebExtension` call, the menu item / strings, the workflow file,
   and the `youtubedl-android` dependencies in `app/build.gradle` plus their
   Proguard rules. No other code depends on them.

## Notes / limitations

- Streams (HLS/DASH) are assembled in memory before being handed to the
  download manager, so very long streams may use a lot of RAM.
- AES-128 encrypted HLS playlists are downloaded but stay encrypted.
- DASH downloads are per-track (video and audio separately) because muxing is
  not available; the picker labels these clearly.
- If a cross-origin fetch is blocked in the page, the download falls back to a
  background-script fetch.
- On the yt-dlp sites the download happens in the app process (not the page), so
  it keeps going if you navigate away. Progress is shown both in the picker row
  and in a low-priority "Downloads" notification.
- The bundled yt-dlp refreshes itself from the official yt-dlp release feed about
  once a week, because that is the part that breaks when a site changes.

## yt-dlp (YouTube and similar sites)

The `youtubedl-android` library (Seal's fork,
`io.github.junkfood02.youtubedl-android`, Maven Central) bundles `yt-dlp`,
CPython and ffmpeg. `NovaYtDlp`:

- initialises `YoutubeDL` **and** `FFmpeg` on a background thread at startup
  (both extract their binaries from `nativeLibraryDir` on first run) and refreshes
  yt-dlp weekly;
- registers a GeckoView background message handler under the name
  **`novaVideoYtdlp`** (via `WebExtension.registerBackgroundMessageHandler`), so
  the extension's background script can call
  `browser.runtime.sendNativeMessage("novaVideoYtdlp", …)`;
- supports `action`s `ping` / `start` / `status` / `cancel`. `start` returns a job
  id immediately and runs the download on a worker thread; the content script
  polls `status` for progress;
- merges video+audio into MP4 with ffmpeg (or extracts MP3 with the "Audio only"
  option) and then publishes the finished file into the public **Downloads**
  collection via MediaStore (on API < 29 it copies to the public Downloads dir
  and runs a media scan).

This is a sideload-only build, so Play Store policy does not apply.
