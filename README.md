# Nova Browser 2.0

A modern, feature-rich Android browser built on **Chromium** — Android System WebView
(the same Blink + V8 engine that powers Chrome on Android) — with a Material-3 Compose UI.

## Features

- **Chromium engine** — Android System WebView (Blink rendering + V8 JS). Full web platform support,
  tiny APK, and the engine ships with Android itself so it stays current without app updates.
- **Real ad blocking** — the EasyList, EasyPrivacy and annoyance filter lists (the same community
  lists used by uBlock Origin) applied via network interception; per-site shield toggle in the
  toolbar, live blocked-count stats, Off / Standard / Strict levels.
- **Safe browsing** — the Chromium engine's built-in Safe Browsing protection.
- **Content-script extensions** — install real extensions from **.crx / .xpi / .zip** packages
  (download one from a page and Nova auto-installs, or pick a file). Content-script-only
  extensions (dark mode, text size, image blocking, etc.) work fully. Three are bundled.
- **Modern UI** — Material 3, dark/light/system themes, gradient start page with live clock,
  speed-dial tiles, pill address bar with suggestions, grid tab switcher, animated progress.
- **Private (incognito) mode** — distinct theme, private sessions.
- Tabs, bookmarks, history, **downloads manager**, external-link handling, search-engine choice,
  desktop-site mode.

## Notes

- Chrome Web Store has **no install flow on Android at all** — even Chrome for Android can't
  "Add to Chrome" (that button is desktop-only). Nova installs extension packages instead, from
  any source (AMO, GitHub releases, .crx/.zip mirrors). Extensions needing background pages or
  browser APIs don't run on WebView — content-script extensions do.
- Ad-block level and safe-browsing changes apply to pages loaded afterwards; the per-site shield
  toggle is instant.
- Built by GitHub Actions on the `build` branch; the debug APK is the `nova-browser-debug-apk` artifact.

## Build

```bash
./gradlew assembleDebug   # APK at app/build/outputs/apk/debug/
```
