# Nova Browser

**Nova Browser** is a personal, privacy-focused Android browser built from the
genuine open-source Firefox engine (the Iceraven fork of Firefox for Android),
branded for you and shipped with real ad blocking built in — no extensions to
set up, no host files to manage by hand.

## What's inside

- **Ad blocking that just works**
  - **uBlock Origin** is bundled and active out of the box — YouTube ads,
    banners, trackers and pop-ups are blocked automatically.
  - **Nova Ad Block** (Settings → Nova Ad Block) lets you fine-tune blocking:
    - block or allow individual domains (one per line),
    - paste any **hosts file** blocklist (StevenBlack hosts, AdAway, …),
    - pull a big list in one tap: **StevenBlack hosts**, **AdAway hosts** or
      **EasyList** preset buttons,
    - import any hosts list from a URL.
  - Blocking is instant — even 15,000+ domain lists don't slow down pages.
- **Allow background playback (per site)** — a toggle in the three-dot menu on
  any site keeps the site running with audio when you lock the screen or leave
  the app. It works on **every site, including YouTube** — Nova keeps the page
  reported as visible, so even sites that pause themselves when hidden keep
  playing. It is remembered per site.
- **In-app update notifications** — when a new version is released you get a
  notification with **Download** (grabs the APK automatically), **GitHub**
  (open the releases page) and **Later** (remind again in 24 hours).
- **Close tabs when the app is closed** — Settings → Tabs → Close tabs → *When the app is closed*.
- **Delete browsing data on quit** — clear history, cookies and cache when you close the app.
- **Install local add-ons** — Settings → Advanced → *Install local add-on* accepts
  Firefox-format extensions (`.zip` / `.xpi`).
- **Your build, your name** — signed with your own key, package `com.nova.browser`,
  and the About screen reads **v1.2**.

## Install

1. Download the latest APK from the [Releases](https://github.com/codegeasse1/nova-browser/releases)
   page (currently `Nova.Browser.1.2.apk`).
2. When Android asks, allow installing apps from your file manager.
3. Open the APK and install. Future updates install over the top — no need to
   uninstall first.

## How it's built

The APK is built automatically by GitHub Actions:

- The workflow lives at `.github/workflows/build-iceraven.yml` and runs on the
  `build` branch (manual runs supported too).
- It clones the pristine [Iceraven 2.46.0](https://github.com/fork-maintainers/iceraven-browser)
  source, applies `nova-branding.sh` (branding, bundled add-ons, settings), and
  assembles a **signed release APK** using the release key stored in repo secrets.
- The finished APK is uploaded to the [Releases](https://github.com/codegeasse1/nova-browser/releases)
  page and kept as a workflow artifact as a backup.

> The `main` branch holds this documentation. CI only runs on pushes to `build`,
> so editing docs here never triggers a build.

## Privacy

Nova Browser keeps your browsing to yourself:

- no account or history sync to any cloud by default,
- built-in blockers stop ads and trackers before they load,
- the "delete browsing data on quit" option wipes your history on close if you want it to.

## Feedback

Found a problem or want a feature? Open an issue on this repository.
