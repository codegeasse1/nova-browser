# Changelog

All notable changes to Nova Browser are listed here, newest first.

## [1.4.6] - 2026-09-24

### Changed
- **Video downloader only.** The experimental Nova PiP mode has been removed, leaving the built-in video downloader as the only Nova feature added to video pages.
- **Cleaner video pages.** The old PiP button, native PiP bridge, PiP setting, and related UI have been removed.
- **Downloader updated.** The bundled video extension was bumped to ensure the updated downloader code is installed correctly.

### Notes
- This release is based on the latest successful video-downloader build from the feature branch.
- Package is `com.nova.browser`.

## [1.4.5] - 2026-09-13

### Added
- **Built-in video downloader.** A download button appears when you play a video on YouTube (and other sites supported by the bundled `yt-dlp` engine). Tap it to open the Nova download panel and save the video.
- **Live download progress.** Every item shows the percentage, downloaded / total size, current speed and estimated time remaining while it downloads.
- **Gallery-friendly downloads.** Videos are saved as MP4 (H.264 video + AAC audio) to your device's Downloads folder, so they play in your phone's stock Gallery as well as VLC/MX Player, and they also appear in the browser's Downloads list.

### Improved
- Closing the download panel (the X) no longer cancels an in-progress download — use the **Cancel** button on a row to cancel just that item.
- Downloads keep running while Nova Browser is in the background or the screen is locked.

### Notes
- Package is `com.nova.browser`. If you have an older Nova build installed, uninstall it first, then install this APK.
