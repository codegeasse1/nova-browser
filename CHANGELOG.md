# Changelog

All notable changes to **Nova Browser** are documented in this file.

Everything before 1.0 was developed as numbered internal test builds
(`2.46.0-fix1` → `2.46.0-fix9`) and is folded into this first release.

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
