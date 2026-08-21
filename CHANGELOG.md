# Changelog

All notable changes to **Nova Browser** are documented in this file.

Everything before 1.0 was developed as numbered internal test builds
(`2.46.0-fix1` → `2.46.0-fix9`) and is folded into this first release.

## [1.4.1] — 2026-08-21

### Fixed

- **Password CSV import now actually imports.** CSVs without a Firefox-style
  `formActionOrigin` column (Chrome/Quetta style, e.g.
  `name,url,username,password,note`) were accepted but every row silently
  failed to save and the app reported "Imported 0 passwords". Imports now use
  the site's own origin as the form action when the file doesn't provide one,
  so the rows are stored for real.
- Import now also recognises more CSV header variants (Quetta's `name,url,username,password,note`,
  `website`, `user`, …) and, for headerless files, guesses the columns from the
  data, so Chrome, Edge, Brave, Quetta and Firefox exports all import correctly.

## [1.4.0] — 2026-08-21

### Added

- **Password import & export (CSV)** — in Settings → Passwords:
  - **Import passwords from CSV** — pick a CSV file exported by any other
    browser (Firefox, Chrome, Edge, Brave, …) and its usernames and passwords
    are added to Nova. Both the Firefox format
    (`url,username,password,httpRealm,formActionOrigin,…`) and the Chrome format
    (`name,url,username,password`) are accepted.
  - **Export passwords to CSV** — writes every saved password to a CSV file in
    the Firefox export format and opens the share sheet, so you can save it
    anywhere or send it to another device, then import it into any other
    browser. A note: the file is **plain text** — treat it like a master
    password file and delete it after importing.

## [1.3.3] — 2026-08-21

### Removed

- **"Allow PiP mode for this site" has been removed.** Entering picture-in-picture
  from a site kept crashing the app when the browser went to the background (on
  DRM and non-DRM sites alike), and the crash could not be made reliable, so the
  option is gone. "Allow background playback" still covers all non-DRM sites;
  DRM video (YouTube) keeps playing in the background on the lock screen where
  Android keeps the window surface alive.

### Fixed

- **Update notifications now actually appear.** Android 13+ silently drops
  notifications from apps that were never granted the notification permission.
  Nova now asks for it on first launch, so the "update available" notification
  shows up the next time a new version is released.

## [1.3.2] — 2026-08-21

### Fixed

- **Crash when going to the background on a site with "Allow PiP mode" enabled**
  while nothing is playing. Android 12+ refuses to start a background keep-alive
  service unless the app is exempt (e.g. it is actively playing audio), and that
  refusal used to crash the app. The keep-alive start is now skipped quietly when
  the system forbids it, so backgrounding any site is always safe.

## [1.3.1] — 2026-08-21

### Fixed

- **Keyboard backspace bug after the screen is locked** — the background
  keep-alive no longer forces input focus onto the hidden page, which used to
  desync the keyboard (backspace needed several presses to delete one character).
- **Mini/floating (freeform) windows** — opening Nova in a mini window no longer
  wipes your tabs, and the "display already acquired" crash when a window
  relaunches is handled instead of crashing.
- Keep-alive now also runs for sites with "Allow PiP mode" enabled, so the page
  stays reported as visible inside the PiP window.

## [1.3] — 2026-08-21

### Added

- **Allow PiP mode (per site)** — a toggle in the three-dot menu. Turn it on for
  a site and when you leave the app while that site is playing media, Nova
  automatically enters a small picture-in-picture window. This keeps DRM video
  (YouTube and other Widevine content) playing in the background and on the lock
  screen, which plain background playback can't do. The toggle is remembered per
  site.

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
