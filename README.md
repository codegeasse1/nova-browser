# Nova Browser

Nova Browser is built on the **real IceRaven** source — the exact upstream code and build steps
(Fenix 153.0, GeckoView `153.0.20260715202819`), untouched engine and features — with a **Nova
layer** on top: name, icon, colors, package, and a few extra Nova options.

- **Engine/source:** [fork-maintainers/iceraven-browser](https://github.com/fork-maintainers/iceraven-browser), tag `iceraven-2.46.0`, incl. its `android-components` submodule
- **Build:** exactly upstream's own CI steps (`install-sdk.sh`, `patch_android_components.sh`,
  the branding string replacements), then `./gradlew app:assembleForkRelease -PversionName=...`
- **Nova layer:** [`nova-branding.sh`](nova-branding.sh) — app name "Nova Browser", package
  `com.nova.browser`, version `2.46.0`, teal accent palette, custom "N" launcher icon + splash
  (icons pre-baked in [`nova-icons/`](nova-icons/), no ImageMagick needed at build time),
  "What's new" pointing at this repo. Cosmetic only.
- **Nova options (Settings → Nova Browser):**
  - **Pause history** — stops saving your browsing history while on.
  - **Study mode** — keeps a study list of visited sites instead of normal history; the History
    screen shows that study list. Works even while Pause history is on, and deletions from the
    History screen (individual items or clear-all) also clean the study list.
  - **Clear tabs on close** — closes all tabs when you remove Nova Browser from the app switcher
    (backgrounding alone does not clear them). Closing the app this way also runs the built-in
    "Delete browsing data on quit" cleanup (Settings → Delete browsing data on quit → check
    **Open tabs** and **Browsing history**), so you get the same wipe as "Quit Nova Browser"
    without having to tap Quit — just swipe Nova away from your recent apps.
- **Output:** `app-debug.apk` (arm64-v8a, debug-signed) → uploaded to release **v3.0**

## Download

https://github.com/codegeasse1/nova-browser/releases/download/v3.0/app-debug.apk

This is a NEW app (package `com.nova.browser`). **Uninstall the old Nova app first**, then install.

## Rebuild

Push to the `build` branch, or run **Actions → "Build Nova Browser APK" → Run workflow**.
First build takes a long time (SDK/NDK/Gradle download + full Fenix compile).
