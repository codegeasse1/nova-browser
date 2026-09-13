# Changelog

All notable changes to Nova Browser are listed here, newest first.

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
