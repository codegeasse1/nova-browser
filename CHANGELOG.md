# Changelog

All notable changes to **Nova Browser** are documented in this file.

Everything before 1.0 was developed as numbered internal test builds
(`2.46.0-fix1` → `2.46.0-fix9`) and is folded into this first release.

## [1.3] — 2026-08-21

### Added

- **Allow PiP mode for this site** — a new toggle in the three-dot menu, under
  "Allow background playback". DRM-protected video (YouTube and other Widevine
  content) used to stop when Nova was backgrounded, because GeckoView's
  compositor needs a window surface. A PiP window keeps a real surface alive, so
  DRM video keeps playing in the background and on the lock screen.

### Fixed

- **Mini / floating window crash** — "Close tabs when the app is closed" and
  "Delete browsing data on quit" no longer fire while Nova is being used in a
  mini, floating or split-screen window (those windows made the task look like
  it vanished, so Nova wiped all tabs and data and crashed on startup).
- **Keyboard desync after screen lock** — background playback no longer forces
  input focus while the screen is locked, which left the keyboard out of sync
  on return (backspace needed several presses to delete one character).

## [1.2] — 2026-08-20

### Fixed

- **Background playback now really works on every site** — including YouTube.
  Sites pause when a browser reports the page as hidden, so Nova now keeps the
  enabled site's page reported as visible while the app is in the background
  (screen locked or app switcher) and its audio/timers keep running. This is
  the Brave-style behaviour: with "Allow background playback" toggled on for a
  site, playback continues in the background no matter what the site itself
  supports.

## [1.1] — 2026-08-20

### Added

- **Allow background playback (per site)** — a toggle in the browser's
  three-dot menu on any site. Turn it on and that site keeps playing audio
  (YouTube, music, AI chat replies, …) when you lock the screen or switch to
  another app. The toggle is remembered per site and playback stops when you
  come back to the browser.
- **In-app update notifications** — when a new release is published, the app
  shows a notification the next time you open it with three options:
  - **Download** — downloads the new APK automatically so you can install it,
  - **GitHub** — opens the releases page so you can grab it manually,
  - **Later** — reminds you again in 24 hours.

## [1.0] — 2026-08-20

First release. Nova Browser is a fully branded, privacy-focused Android
browser with ad blocking built in.

### Added

- **uBlock Origin bundled and enabled** — full ad blocking out of the box
  (YouTube ads, banners, trackers, pop-ups).
- **Nova Ad Block** — built-in blocker reachable from Settings → Nova Ad Block:
  - block or allow individual domains,
  - paste any hosts-file blocklist (StevenBlack hosts, AdAway, …),
  - one-tap presets: **StevenBlack hosts**, **AdAway hosts**, **EasyList**,
  - import any hosts list from a URL.
- **Fast host matching** — even 15,000+ entry blocklists don't slow down page loads.
- **Close tabs when the app is closed** (Settings → Tabs).
- **Delete browsing data on quit** — wipes history, cookies and cache on close when enabled.
- **Install local add-ons** (Settings → Advanced) — Firefox-format `.zip` / `.xpi` extensions.
- **Full rebrand** — signed with its own key, package `com.nova.browser`,
  About screen shows **v1.0**, and no upstream version strings appear anywhere.
