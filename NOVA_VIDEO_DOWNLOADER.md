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
- tapping the icon opens a compact picker (at most 45% of the screen height) listing
  the detected items, with a 32px `X` to close it and tap-outside-to-close;
- for HLS it reads the master playlist and offers every quality;
- for DASH it lists the video/audio representations;
- downloads by assembling the bytes in the page and handing them to the normal
  Fenix download flow (blob download), so the standard download notification and
  Downloads list are used.

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
| `app/src/main/assets/extensions/nova-video/manifest.json` | new — MV2 manifest of the bundled extension |
| `app/src/main/assets/extensions/nova-video/background.js` | new — network sniffing, playlist/manifest parsing, binary fetch fallback |
| `app/src/main/assets/extensions/nova-video/content.js` | new — icon-only UI + downloader picker (shadow DOM) |
| `app/src/main/java/org/mozilla/fenix/components/NovaVideoDownloader.kt` | new — preference + engine enable/disable helper |
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
   helper, the `NOVA_VIDEO_ADDON_ID` constant + `installBuiltInWebExtension` call,
   the menu item / strings, and the workflow file. No other code depends on them.

## Notes / limitations

- Streams (HLS/DASH) are assembled in memory before being handed to the
  download manager, so very long streams may use a lot of RAM.
- AES-128 encrypted HLS playlists are downloaded but stay encrypted.
- DASH downloads are per-track (video and audio separately) because muxing is
  not available; the picker labels these clearly.
- If a cross-origin fetch is blocked in the page, the download falls back to a
  background-script fetch.
- YouTube is not covered yet (see the yt-dlp note below).

## Roadmap: yt-dlp for YouTube

`yt-dlp` integration is planned as a separate step on this same branch. The
approach is to bundle the open-source `youtubedl-android` library, initialise it
in `FenixApplication`, and add a native ↔ extension bridge (a-c
`WebExtension.registerBackgroundMessageHandler` / GeckoView message delegate with
`browser.runtime.sendNativeMessage`) so the extension can hand a page URL to
yt-dlp, which extracts and writes the file into Downloads. This is a sideload-only
build, so Play Store policy does not apply.
