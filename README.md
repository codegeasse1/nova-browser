# Nova Browser

Nova Browser is built on the **real IceRaven** source — the exact upstream code and build steps
(Fenix 153.0, GeckoView `153.0.20260715202819`), untouched engine and features — with a **Nova
branding layer** on top: name, icon, colors and package.

- **Engine/source:** [fork-maintainers/iceraven-browser](https://github.com/fork-maintainers/iceraven-browser), tag `iceraven-2.46.0`, incl. its `android-components` submodule
- **Build:** exactly upstream's own CI steps (`install-sdk.sh`, `patch_android_components.sh`,
  the branding string replacements), then `./gradlew app:assembleForkRelease -PversionName=...`
- **Nova layer:** [`nova-branding.sh`](nova-branding.sh) — app name "Nova Browser", package
  `com.nova.browser`, teal accent palette, custom "N" launcher icon + splash. Cosmetic only.
- **Output:** `app-debug.apk` (arm64-v8a, debug-signed) → uploaded to release **v3.0**

## Download

https://github.com/codegeasse1/nova-browser/releases/download/v3.0/app-debug.apk

This is a NEW app (package `com.nova.browser`). **Uninstall the old Nova app first**, then install.

## Rebuild

Push to the `build` branch, or run **Actions → "Build Iceraven APK" → Run workflow**.
First build takes a long time (SDK/NDK/Gradle download + full Fenix compile).
