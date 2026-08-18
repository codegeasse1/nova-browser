# Nova Browser 2.0

A modern, feature-rich Android browser built on **GeckoView** (Mozilla's engine — the same engine behind
Quetta Browser and Firefox for Android), with a Material-3 Compose UI.

## Features

- **Real browser engine** — GeckoView (nightly channel), not a WebView wrapper. Full web platform support,
  modern rendering, independent content-process sandboxing.
- **WebExtension support** — install real Firefox/WebExtensions:
  - from **addons.mozilla.org** directly inside the browser (install prompts auto-confirmed),
  - from local **.zip / .crx / .xpi files** via the file picker,
  - a bundled sample extension is pre-installed.
- **Ad blocking / Enhanced Tracking Protection** — blocks ads, trackers, analytics, social, cryptominers and
  fingerprinters via GeckoView content blocking; per-site shield toggle in the toolbar; live blocked-count stats.
- **DNS over HTTPS** — automatic (fallback) or strict modes, with selectable provider (Mozilla / Cloudflare / NextDNS).
- **Modern UI** — Material 3, dark/light/system themes, gradient start page with live clock, speed-dial tiles,
  pill address bar with suggestions, grid tab switcher, animated progress.
- **Private (incognito) mode** — distinct theme, private sessions.
- Tabs, bookmarks, history, downloads, external-link handling, search-engine choice.

## Notes

- DoH and ad-block **level** changes take effect on the next app launch (GeckoView runtime setting);
  the per-site shield toggle works instantly.
- Chrome Web Store extensions can't be installed from Google's store (GeckoView runs WebExtensions, not Chrome
  extensions) — but many popular extensions are available on AMO or as .zip/.crx files you can import.
- Built by GitHub Actions on the `build` branch; the debug APK is the `nova-browser-debug-apk` artifact.

## Build

```bash
./gradlew assembleDebug   # APK at app/build/outputs/apk/debug/
```
