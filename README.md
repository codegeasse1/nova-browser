# Nova Browser

A lightweight Android browser with **Chrome-extension support** — install extensions from
`.zip`/`.crx` files and they run: content scripts, background pages, storage, messaging
and popups. Kiwi-style extension loading, without the multi-GB Chromium build.

## Features

- Tabs (up to 12), address bar with Google/DuckDuckGo/Bing search, back/forward/reload
- Bookmarks, history, private browsing, homepage + search-engine settings
- **Extensions screen**: install from `.zip`/`.crx`, enable/disable, open popup, remove
- Extension runtime (subset of the Chrome APIs):
  - `chrome.runtime` (`id`, `sendMessage`, `onMessage`)
  - `chrome.storage.local` / `sync` (`get` / `set` / `remove` / `clear`)
  - `chrome.tabs` (`query`, `create`, `update`, `getCurrent`)
  - `chrome.action` / `browserAction` (`setBadgeText`)
  - Content scripts matched with Chrome host-match patterns, injected at
    `document_start` / `document_idle`, with `js` + `css`
  - MV2 background pages (and MV3 service workers approximated as background scripts)
- Bundled sample extension to try it instantly: **Extensions → Load sample**

## Building

`./gradlew assembleDebug` — the APK lands in `app/build/outputs/apk/debug/`.

## Extension compatibility

This runs a **lightweight extension runtime** on top of Android's WebView — it is not the full
Chromium extension engine. Simple extensions (content-script tweaks, storage-based options,
badges, simple background logic) work well. Complex MV3 extensions (service workers,
`webRequest` blocking, devtools, NPAPI/PPAPI, heavy permissions) will not work.

## How the extension runtime works

1. Every WebView gets a `novaBridge` JavascriptInterface.
2. A small `chrome.*` shim is injected before each extension's scripts run.
3. Content scripts are injected into pages at `document_start`/`document_idle` when the URL
   matches the extension's `content_scripts.matches`.
4. Background pages run in a hidden WebView; messages route through the bridge; storage is
   JSON files in the app's data directory.
