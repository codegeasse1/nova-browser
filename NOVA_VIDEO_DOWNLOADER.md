# Nova Video Downloader (feature branch only)

This branch (`feature/video-downloader`) adds a built-in **video/audio downloader**
to Nova Browser. It is intentionally self-contained so it can be removed by simply
deleting this branch â nothing on `main` or in the release workflow is touched.

## What it does

A third bundled WebExtension (`nova-video@nova.browser`) that:

- watches network traffic and detects media the page loads â direct files
  (`Content-Type: video/*` / `audio/*`, or a known extension) and streaming
  manifests (HLS `.m3u8`, DASH `.mpd`);
- shows **one small (38px) download-arrow icon**, but only as a brief *peek*: it
  fades in for ~2.5s when the page discovers media and then fades out again. It
  comes back for ~2.5s whenever a video starts or pauses, when the page is tapped
  (or the pointer rests on it), and while the picker is open. There is no other
  on-screen chrome: no floating panel, no "Rescan" button, no "Hide here" dialog.
  The icon is draggable and hides itself while a video is fullscreen;
- tapping the icon opens a compact picker (at most 40% of the screen height) listing
  the detected items, with a 32px `X` to close it and tap-outside-to-close.
  Closing the picker never cancels a download: a yt-dlp download keeps running in
  the background (tracked natively, with a progress notification), and only the
  row's **Cancel** button stops it;
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

## In-page player (optional)

A second switch, **"Inbuilt video player"**, adds Nova's own player controls over
the page's `<video>` - a phone-style player, not a bare toolbar. When the switch
is on and a video **starts playing** - or the user **taps one** - Nova's controls
come up over the video, in its normal inline position, and fade out again ~3s
later. Nova **never** takes the video full screen on its own and **never** moves
it in the DOM, so the site's own player keeps working underneath.

- controls come up on **play, pause and tap**, and hide again after **~3s**
  (`SHOW_MS`) whether the video is playing or paused - the behaviour of every
  phone player. Any tap, play/pause, seek or button press brings them straight
  back, and they stay up while the pointer moves over the video;
- the inline bar is a seek row (`0:00` / slider / duration) plus **one** row of
  icons: play/pause, back-10, forward-10, mute + volume slider, brightness (the
  sun button cycles presets inline; fullscreen has a fine slider), playback
  speed (0.5x ... 2x), the settings (3-dot) button, rotate, fullscreen, download
  (opens the same picker as the icon; top frame only) and an `x` that hides the
  bar. The bar is clamped to at least 220px and switches to a "tight" layout
  under 520px so the icons stay on one row over a phone-sized video, with the
  slack spread evenly instead of pooling into one gap;
- **fullscreen** (only ever from its own button; Escape leaves) restyles the
  video to the viewport with Nova's inline styles and turns the controls into a
  real player: a top bar with a back button, the page title and the settings
  button, and a full-width bottom control bar. Rotate only exists there. Leaving
  fullscreen restores the video's original inline styles untouched;
- the **3-dot button** opens a *Nova Player* settings sheet (a bottom sheet in
  portrait, a right-hand side panel in landscape, laid out like Quetta's player):
  **Subtitles** (toggles the video's own text track, or says it has none),
  **Quality** (lists whatever levels the page exposes - an `hls.js` instance hung
  off the element or the window, or `<source>` elements with size hints - and
  switches between them; with only one level it shows the decoded resolution and
  says the site controls the rest), **Playback Speed**, **Repeat** (off / loop /
  repeat-one; repeat-one restarts by hand on `ended`, since the `loop` flag
  repeats seamlessly) and **Sleep Timer** (5/15/30/60 min, then pauses playback).
  The controls stay up while the sheet is open; it closes on `x`, on Escape, or
  on a tap outside it;
- the bar is anchored to the video's own rectangle (clamped to the viewport) and
  follows it on scroll/resize. A transient bad rectangle (a site re-laying the
  video out for a frame) no longer blinks the bar away: it has to stay bad for
  several frames before the bar gives up;
- **a site that fullscreens its own video no longer makes Nova vanish.** The old
  handler hid the *whole* Nova host while any element was fullscreen, so tapping
  a video on such a site showed Nova's controls for a moment and then hid them
  for the rest of the page's life - the "it flashes for a nanosecond and
  disappears" bug. Now a video entering native fullscreen is handed straight to
  Nova's own fullscreen (rate-limited, so a site that re-requests fullscreen
  cannot cause a loop), and only the download button is put away while the page
  itself is fullscreen - it lives in the same host, which is why hiding the host
  killed the player too.

A paused video the user has not touched is left alone (no bar), so loading a page
with a paused video never clutters it. The bar is kept painted by the same
keep-alive CSS animation as the download icon, refreshed by the poll.

Videos hidden inside **open shadow roots** are handled too: media events are
matched via `composedPath()` (not `event.target`, which is retargeted to the
shadow host) and a throttled, capped scan reaches shadow-root videos that a plain
`document.querySelectorAll("video")` cannot see.

The player also runs inside frames (`all_frames`), because most embedded players
live in an iframe where the top frame cannot see their `<video>` at all. In a
frame it shows for a video that is **playing**, or one the user has **tapped** -
so a page full of paused embeds does not sprout bars everywhere. A small paused
video (a thumbnail or decorative loop) is likewise left alone until it is tapped,
so a page carrying a 140px preview never gets a full bar parked over it.

Switching it off restores the video's inline styles and removes the bar without a
page reload, and a switch flip is picked up within ~2s (the content script polls
its preferences). The player is independent of the downloader switch - with the
downloader off, the player still works and its download button is hidden.

## On / off switch in the 3-dot menu

The feature can be switched off without uninstalling anything. The browser's
3-dot menu has two related items, both with on/off switches (same pattern as the
existing "Allow background playback" switch):

- **"Video Downloader"** - the download icon + picker.
- **"Inbuilt video player"** - Nova's own player controls over videos.

Turning the downloader **on** enables the bundled extension in the engine
(`EnableSource.USER`); turning it **off** disables the extension once the player
is off too. Turning the player **on** keeps the extension enabled and makes the
content script draw its controls; turning it **off** removes them.

- The extension is disabled only when **both** switches are off, because it is
  the vehicle for both features (see `NovaVideoDownloader.isExtensionWanted`).
- When the extension is disabled its content scripts stop running and the
  already-injected icon/panel/player tear themselves down (they notice the dead
  extension context), so nothing is shown while browsing.
- Both choices are stored in shared preferences (`NovaVideoDownloader` /
  `novaVideoDownloaderEnabled`, default on; `NovaInbuiltPlayer` /
  `novaInbuiltPlayerEnabled`, default off) and re-applied on launch, so they
  survive restarts.
- The content script reads them at runtime through the native bridge
  (`novaVideoYtdlp`, action `prefs`), polls every few seconds and on page focus,
  so a toggle takes effect without reloading the page.

## Files added / changed

| File | Change |
| --- | --- |
| `app/src/main/assets/extensions/nova-video/manifest.json` | new â MV2 manifest of the bundled extension (has the `nativeMessaging` **and** `geckoViewAddons` permissions) |
| `app/src/main/assets/extensions/nova-video/background.js` | new â network sniffing, playlist/manifest parsing, binary fetch fallback, yt-dlp bridge |
| `app/src/main/assets/extensions/nova-video/content.js` | new â icon-only UI + downloader picker (shadow DOM), incl. the yt-dlp site entry |
| `app/src/main/java/org/mozilla/fenix/components/NovaVideoDownloader.kt` | new â preference + engine enable/disable helper |
| `app/src/main/java/org/mozilla/fenix/components/NovaYtDlp.kt` | new â native yt-dlp bridge (init, downloads, progress, MediaStore publish) |
| `app/build.gradle` | adds the `youtubedl-android` `library` + `ffmpeg` dependencies |
| `app/src/main/java/org/mozilla/fenix/components/NovaInbuiltPlayer.kt` | new - shared-preference switch for the in-page player |
| `app/src/main/assets/extensions/nova-video/content.js` | icon auto-hides (peek behaviour); optional in-page player (control bar over the video, ~3s auto-hide on play/pause/tap, own fullscreen with a top bar, 3-dot settings sheet for subtitles/quality/speed/repeat/sleep; the `<video>` is never moved), incl. shadow-DOM and frame support |
| `app/src/main/assets/extensions/nova-video/manifest.json` | version `1.2.2` - bumped so the built-in extension is re-installed (GeckoView skips a built-in whose version is unchanged, which would leave the old `content.js` on device) |
| `app/proguard-rules.pro` | keep/dontwarn rules for youtubedl-android, Jackson and commons-io |
| `app/src/main/java/org/mozilla/fenix/FenixApplication.kt` | `NOVA_VIDEO_ADDON_ID` constant + `installBuiltInWebExtension(...)` call + re-applies the stored on/off preference |
| `app/src/main/java/org/mozilla/fenix/components/menu/compose/MainMenu.kt` | new "Video Downloader" menu item with a switch |
| `app/src/main/java/org/mozilla/fenix/components/menu/MenuDialogFragment.kt` | wires the switch state + toggles the extension |
| `app/src/main/res/values/strings.xml` | `browser_menu_video_downloader` (+ `_on` / `_off`) strings |
| `.github/workflows/build-video-downloader.yml` | new â builds and signs the APK and uploads it as a **workflow artifact only** (no GitHub Release) |
| `NOVA_VIDEO_DOWNLOADER.md` | new â this file |

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
2. Revert the commit and remove the `nova-video` assets, the `NovaVideoDownloader`,
   `NovaInbuiltPlayer` and `NovaYtDlp` helpers, the `NOVA_VIDEO_ADDON_ID` constant +
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
  `browser.runtime.sendNativeMessage("novaVideoYtdlp", â¦)`;
- supports `action`s `ping` / `prefs` / `start` / `status` / `cancel` / `list`. `start`
  returns a job id immediately and runs the download on a worker thread; the
  content script polls `status` for progress and uses `list` to re-attach to a
  running download after a page reload or after the picker was closed;
- merges video+audio into MP4 with ffmpeg (or extracts MP3 with the "Audio only"
  option) and then publishes the finished file into the public **Downloads**
  collection via MediaStore (on API < 29 it copies to the public Downloads dir
  and runs a media scan). For video it prefers **H.264 + AAC in an MP4**:
  `-f bv*[vcodec^=avc1]+ba[acodec^=mp4a]/b[vcodec^=avc1][acodec^=mp4a]/b[ext=mp4]/b`,
  `-S vcodec:h264,acodec:aac,res:1080`, `--merge-output-format mp4` and
  `--postprocessor-args Merger:-movflags +faststart`. This matters because
  Android's own gallery / `MediaExtractor` cannot decode VP9/AV1-with-Opus MP4s:
  those files play fine in VLC / MX Player (which bundle their own codecs) but
  show up as "broken"/audio-only in the stock gallery.

This is a sideload-only build, so Play Store policy does not apply.

## Gotchas (why the code looks the way it does)

- **Adopted stylesheets beat `<style>` elements inside a shadow root.** The
  player's extra CSS has to be *adopted* (appended to
  `shadowRoot.adoptedStyleSheets`), not injected as a `<style>` element: the base
  sheet wins every conflict against a later `<style>`, which silently collapsed
  the seek bar's `flex: 1 1 auto` into a 40px stub and oversized every button.
  The player applies its stylesheet as a second adopted sheet for exactly this
  reason (a `<style>` element is only the fallback for engines without
  constructable stylesheets, and it is safe there because it is the only sheet).

- **`geckoViewAddons` is mandatory.** `manifest.json` must list it alongside
  `nativeMessaging`, otherwise GeckoView's `ExtensionParent.openNative` takes the
  desktop native-messaging path and `sendNativeMessage` fails with a generic
  "An unexpected error occurred".
- **Register the bridge on the main thread.** `WebExtension.registerBackgroundMessageHandler`
  ends up in `setMessageDelegate`, which is `@UiThread`. If it throws, the
  exception is swallowed and the extension's `sendNativeMessage` promise never
  settles (the controller queues messages for a name with no delegate). `NovaYtDlp`
  therefore posts registration to the main `Looper` and retries.
- **Never let the UI depend on one native reply.** The native `list`/`status`
  replies carry the page `url`, and the content script builds the job row
  *before* the bridge answers: a job without a native id yet is matched against
  the native list by URL on a 1s pump (for up to two minutes). So a slow first
  `start` reply shows "Waiting for the downloader..." for a moment and then
  switches to real progress, instead of failing. Every native call is also raced
  against a timeout, and the background warms the bridge with a `ping` at
  extension load so the very first tap is fast.
- **Closing the UI never cancels.** Download state lives natively; the content
  script polls it on its own timer (`pumpYtDlp`) which runs whether or not the
  picker is open, and re-attaches via `list` after a reload. Only the row's Cancel
  button calls the native `cancel` action.
