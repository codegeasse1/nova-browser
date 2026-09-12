# Nova Video Downloader (feature branch only)

This branch (`feature/video-downloader`) adds a built-in **video/audio downloader**
to Nova Browser. It is intentionally self-contained so it can be removed by simply
deleting this branch — nothing on `main` or in the release workflow is touched.

## What it does

A third bundled WebExtension (`nova-video@nova.browser`) that:

- watches network traffic and detects media the page loads — direct files
  (`Content-Type: video/*` / `audio/*`, or a known extension) and streaming
  manifests (HLS `.m3u8`, DASH `.mpd`);
- shows a small draggable floating button (only when media is found, never while
  a video is fullscreen) that opens a picker with the detected items;
- for HLS it reads the master playlist and offers every quality;
- for DASH it lists the video/audio representations;
- downloads by assembling the bytes in the page and handing them to the normal
  Fenix download flow (blob download), so the standard download notification and
  Downloads list are used.

It also reports `<video>`/`<audio>` elements found in the page (including frames).

## Files added / changed

| File | Change |
| --- | --- |
| `app/src/main/assets/extensions/nova-video/manifest.json` | new — MV2 manifest of the bundled extension |
| `app/src/main/assets/extensions/nova-video/background.js` | new — network sniffing, playlist/manifest parsing, binary fetch fallback |
| `app/src/main/assets/extensions/nova-video/content.js` | new — floating button + downloader picker UI (shadow DOM) |
| `app/src/main/java/org/mozilla/fenix/FenixApplication.kt` | 3 lines changed — `NOVA_VIDEO_ADDON_ID` constant + one `installBuiltInWebExtension(...)` call + comment |
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
2. Merge `main` back / cherry-pick nothing and simply remove the three
   `nova-video` files + the `NOVA_VIDEO_ADDON_ID` constant, the
   `installBuiltInWebExtension` call and the workflow file. No other code
   depends on them.

## Notes / limitations

- Streams (HLS/DASH) are assembled in memory before being handed to the
  download manager, so very long streams may use a lot of RAM.
- AES-128 encrypted HLS playlists are downloaded but stay encrypted.
- DASH downloads are per-track (video and audio separately) because muxing is
  not available; the picker labels these clearly.
- If a cross-origin fetch is blocked in the page, the download falls back to a
  background-script fetch.
