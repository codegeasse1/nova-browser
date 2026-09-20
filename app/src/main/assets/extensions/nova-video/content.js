"use strict";

/*
 * Nova Video Downloader - content script.
 *
 * Every frame reports the <video>/<audio> elements it can see to the
 * background script. The top frame additionally shows a single small download
 * button - and only while the page actually has a downloadable video/audio.
 * Tapping it opens a compact picker.
 *
 * Independently, with "Enable PIP mode" switched on, a second small button
 * peeks over any video for three seconds on play/pause/tap and opens the
 * browser's picture-in-picture window (see the PIP mode section below).
 *
 * There is no other chrome: to turn either feature off use its switch in the
 * browser menu.
 */

(function () {
  const HEARTBEAT_STALE_MS = 5000;
  const prevInstance = window.__novaVideo;
  if (prevInstance && prevInstance.beat && Date.now() - prevInstance.beat < HEARTBEAT_STALE_MS) {
    return;
  }
  try {
    const stale = document.querySelectorAll("#nova-video-host");
    for (const el of stale) {
      if (el.parentNode) el.parentNode.removeChild(el);
    }
  } catch (e) {
    /* ignore */
  }
  const instance = { beat: Date.now() };
  window.__novaVideo = instance;
  function heartbeat() {
    instance.beat = Date.now();
  }

  const IS_TOP = (() => {
    try {
      return window.top === window;
    } catch (e) {
      return false;
    }
  })();

  let contextDead = false;

  function send(type, extra) {
    if (contextDead) return Promise.resolve({ ok: false, error: "context dead" });
    return browser.runtime
      .sendMessage(Object.assign({ type: type }, extra || {}))
      .then(function (res) {
        return res || { ok: false, error: "no response" };
      })
      .catch(function (e) {
        const message = String((e && e.message) || e);
        if (/context invalidated|Extension context|receiving end does not exist/i.test(message)) {
          contextDead = true;
          teardown();
        }
        return { ok: false, error: message };
      });
  }

  /* ---------------------------------------------------------------- */
  /* Element reporting (all frames)                                    */
  /* ---------------------------------------------------------------- */

  function collectElements() {
    const out = [];
    const nodes = document.querySelectorAll("video,audio");
    for (const el of nodes) {
      let src = el.currentSrc || el.getAttribute("src") || "";
      if (!src) {
        const source = el.querySelector("source[src]");
        if (source) src = source.getAttribute("src") || "";
      }
      if (!src) continue;
      let abs = src;
      try {
        abs = new URL(src, location.href).href;
      } catch (e) {
        /* keep as-is */
      }
      out.push({
        url: abs,
        tag: el.tagName.toLowerCase(),
        width: el.videoWidth || 0,
        height: el.videoHeight || 0,
        duration: isFinite(el.duration) ? el.duration : 0,
      });
    }
    return out;
  }

  function reportElements() {
    if (contextDead) return;
    let elements;
    try {
      elements = collectElements();
    } catch (e) {
      return;
    }
    if (!elements.length) return;
    send("novaVideo:reportElements", { elements: elements, title: document.title || "" });
  }

  const reportTimer = setInterval(reportElements, 4000);
  reportElements();
  document.addEventListener("loadedmetadata", reportElements, true);
  document.addEventListener("play", reportElements, true);
  document.addEventListener("loadeddata", reportElements, true);

  /* ---------------------------------------------------------------- */
  /* UI                                                                */
  /* The download button is a top-frame-only affordance; the PiP button */
  /* runs in any frame that owns a <video> (see start()).               */
  /* ---------------------------------------------------------------- */

  let entries = [];
  let panelOpen = false;
  let host = null;
  let shadow = null;
  let btnEl = null;
  let panelEl = null;
  let listEl = null;
  let rowRefs = new Map();
  let hlsInfo = new Map();
  let dashInfo = new Map();
  let ytdlpJobs = new Map();
  let hideTimer = null;
  let prefsTimer = null;
  let prefs = { pip: false, downloader: true };
  let prefsBusy = false;
  let pipEl = null;
  let pipVideo = null;
  let pipMediaBound = [];
  let pipTimer = null;
  let pipUntil = 0;
  let lastAliveAt = 0;
  let pollTimer = null;
  let pos = null;
  let frameTouched = false;
  let prefsPrimed = false;
  let frameTimer = null;
  let framePrefsAt = 0;
  let listenersBound = false;

  function teardown() {
    contextDead = true;
    instance.beat = 0;
    clearInterval(reportTimer);
    clearInterval(pollTimer);
    clearInterval(frameTimer);
    clearTimeout(hideTimer);
    clearTimeout(prefsTimer);
    try {
      destroyPip();
    } catch (e) {
      /* ignore */
    }
    if (host && host.parentNode) host.parentNode.removeChild(host);
    host = null;
    shadow = null;
    btnEl = null;
    panelEl = null;
    listEl = null;
    if (ytdlpPump) clearInterval(ytdlpPump);
    ytdlpPump = null;
    ytdlpListBusy = false;
    ytdlpJobs.clear();
  }

  const CSS = `
    :host { all: initial; }
    * { box-sizing: border-box; }
    [hidden] { display: none !important; }
    .nv-btn {
      position: fixed; width: 38px; height: 38px; border-radius: 12px;
      background: rgba(30,32,40,.92);
      border: 1px solid rgba(255,255,255,.16);
      box-shadow: 0 3px 12px rgba(0,0,0,.45);
      display: flex; align-items: center; justify-content: center;
      cursor: pointer; touch-action: none;
      transition: opacity .3s ease;
      opacity: .92;
    }
    .nv-btn.nv-hide { opacity: 0; pointer-events: none; }
    .nv-btn svg { width: 20px; height: 20px; fill: #7f9dff; pointer-events: none; }
    .nv-panel {
      position: fixed; width: 300px; max-width: calc(100vw - 20px);
      max-height: min(40vh, 340px);
      z-index: 2147483647;
      background: #1b1c21; color: #e9e9ef;
      border: 1px solid #34363f; border-radius: 13px; overflow: hidden;
      box-shadow: 0 12px 34px rgba(0,0,0,.6);
      font: 13px/1.45 -apple-system, system-ui, "Segoe UI", Roboto, sans-serif;
      display: flex; flex-direction: column;
    }
    .nv-head {
      display: flex; align-items: center; gap: 8px; flex: none;
      padding: 8px 8px 8px 12px; border-bottom: 1px solid #2b2d35; background: #202127;
    }
    .nv-head .nv-title { flex: 1; font-weight: 600; font-size: 13px; }
    .nv-x {
      width: 32px; height: 32px; border: 0; border-radius: 9px; cursor: pointer;
      background: #2c2e37; color: #d5d6de; font-size: 18px; line-height: 1;
    }
    .nv-x:hover { background: #3a3c47; }
    .nv-list { min-height: 0; overflow-y: auto; -webkit-overflow-scrolling: touch; padding: 6px; flex: 1; }
    .nv-empty { padding: 16px 12px; color: #9a9cab; text-align: center; }
    .nv-row { padding: 9px 10px; border-radius: 10px; }
    .nv-row + .nv-row { margin-top: 4px; }
    .nv-row-top { display: flex; align-items: center; gap: 7px; }
    .nv-kind {
      flex: none; font-size: 10px; font-weight: 700; letter-spacing: .04em;
      text-transform: uppercase; padding: 2px 6px; border-radius: 6px;
      background: #33364a; color: #a9b4ff;
    }
    .nv-kind.nv-audio { background: #2f3a33; color: #8fe0a8; }
    .nv-kind.nv-hls, .nv-kind.nv-dash { background: #3c3231; color: #ffb184; }
    .nv-kind.nv-ytdlp { background: #432b3a; color: #f7a8d8; }
    .nv-name {
      flex: 1; min-width: 0; white-space: nowrap; overflow: hidden;
      text-overflow: ellipsis; font-size: 12.5px;
    }
    .nv-size { flex: none; color: #8d8fa0; font-size: 11.5px; }
    .nv-meta {
      color: #83859a; font-size: 11px; margin-top: 3px;
      display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
      overflow: hidden; word-break: break-all;
    }
    .nv-actions { display: flex; align-items: center; gap: 6px; margin-top: 7px; flex-wrap: wrap; }
    .nv-go {
      border: 0; border-radius: 8px; padding: 7px 14px; cursor: pointer;
      background: #5847f5; color: #fff; font-size: 12px; font-weight: 600;
    }
    .nv-go:hover { background: #6a5bff; }
    .nv-go[disabled] { opacity: .5; cursor: default; }
    .nv-copy {
      border: 0; border-radius: 8px; padding: 7px 11px; cursor: pointer;
      background: #2c2e37; color: #cfd0d8; font-size: 12px;
    }
    .nv-copy:hover { background: #3a3c47; }
    .nv-cancel { background: #3a2b31; color: #ffb1c1; }
    .nv-cancel:hover { background: #4a353d; }
    .nv-check {
      display: inline-flex; align-items: center; gap: 5px;
      font-size: 12px; color: #cfd0d8; cursor: pointer;
    }
    .nv-check input { margin: 0; }
    .nv-select {
      background: #2c2e37; color: #e9e9ef; border: 1px solid #3a3c47;
      border-radius: 8px; padding: 6px 8px; font-size: 12px; max-width: 150px;
    }
    .nv-status { font-size: 11.5px; color: #8d8fa0; margin-top: 6px; }    .nv-status.nv-err { color: #ff8f9f; }
    .nv-prog { height: 4px; border-radius: 2px; background: #2c2e37; margin-top: 7px; overflow: hidden; }
    .nv-prog > i { display: block; height: 100%; width: 0; background: #5847f5; transition: width .15s; }
    .nv-toast {
      position: fixed; max-width: 280px; background: #26272e; color: #e9e9ef;
      border: 1px solid #3a3c47; border-radius: 10px; padding: 9px 12px;
      z-index: 2147483647;
      font: 12px/1.4 -apple-system, system-ui, sans-serif;
      box-shadow: 0 8px 24px rgba(0,0,0,.5);
    }
    .nv-pip {
      position: fixed; z-index: 2147483647; width: 38px; height: 38px;
      border-radius: 10px; box-sizing: border-box;
      background: rgba(18,19,24,.6); color: #fff;
      border: 1px solid rgba(255,255,255,.3);
      box-shadow: 0 3px 12px rgba(0,0,0,.45);
      backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px);
      display: flex; align-items: center; justify-content: center;
      cursor: pointer; touch-action: none;
      transition: transform .14s ease, background .14s ease, border-color .14s ease, opacity .3s ease;
    }
    .nv-pip.nv-hide { opacity: 0; pointer-events: none; }
    .nv-pip:active { transform: scale(.9); background: rgba(88,71,245,.88); border-color: #5847f5; }
    .nv-pip svg { width: 20px; height: 20px; fill: #fff; pointer-events: none; }
    @keyframes nova-keepalive {
      0%, 91% { visibility: visible; }
      100% { visibility: hidden; }
    }
    .nv-btn, .nv-panel, .nv-toast, .nv-pip { animation: nova-keepalive 2.6s linear forwards; }
  `;

  function applyStyles(root) {
    try {
      const sheet = new CSSStyleSheet();
      sheet.replaceSync(CSS);
      root.adoptedStyleSheets = [sheet];
    } catch (e) {
      const style = document.createElement("style");
      style.textContent = CSS;
      root.appendChild(style);
    }
  }

  /*
   * The chrome is kept visible only while this script is alive. If the add-on
   * is switched off (or its context dies) the script stops calling keepAlive()
   * and the CSS keep-alive animation hides anything it already injected into
   * the page - injected DOM is NOT removed when a content script is unloaded,
   * so this is the only way to guarantee the icon disappears without a reload.
   */
  function keepAlive() {
    lastAliveAt = Date.now();
    const els = [btnEl, panelEl, pipEl, shadow && shadow.querySelector(".nv-toast")];
    for (const el of els) {
      if (!el) continue;
      el.style.animation = "none";
      void el.offsetWidth;
      el.style.animation = "";
    }
  }

  const ICON =
    '<svg viewBox="0 0 24 24"><path d="M12 16.5l-5.5-5.5h3.25V3h4.5v8H17.5L12 16.5zM5 18h14v2.5H5V18z"/></svg>';

  /*
   * The host + shadow root is shared by both modes: the download button only
   * exists in the top frame, while the PiP button can be created in any
   * frame that actually owns a <video> (see start()).
   */
  function buildHost() {
    if (host) return;
    host = document.createElement("div");
    host.id = "nova-video-host";
    host.style.cssText =
      "all:initial;position:fixed;top:0;left:0;width:0;height:0;z-index:2147483647;";
    shadow = host.attachShadow({ mode: "open" });
    applyStyles(shadow);
    (document.body || document.documentElement).appendChild(host);
    ensureListeners();
  }

  function buildUi() {
    buildHost();

    btnEl = document.createElement("div");
    btnEl.className = "nv-btn";
    btnEl.title = "Download video";
    btnEl.innerHTML = ICON;
    btnEl.hidden = true;

    panelEl = document.createElement("div");
    panelEl.className = "nv-panel";
    panelEl.hidden = true;
    panelEl.innerHTML =
      '<div class="nv-head"><span class="nv-title">Download video</span>' +
      '<button class="nv-x" type="button" title="Close" aria-label="Close">\u00d7</button></div>' +
      '<div class="nv-list"></div>';

    shadow.appendChild(btnEl);
    shadow.appendChild(panelEl);
    listEl = panelEl.querySelector(".nv-list");

    const closeBtn = panelEl.querySelector(".nv-x");
    const doClose = function (e) {
      if (e) {
        e.preventDefault();
        e.stopPropagation();
      }
      closePanel();
    };
    closeBtn.addEventListener("click", doClose);
    closeBtn.addEventListener("touchend", doClose);
    closeBtn.addEventListener("pointerup", doClose);

    setupDrag();
    window.addEventListener("resize", positionPanel);
    positionButton();
    keepAlive();
  }

  function videoAtPoint(x, y) {
    let best = null;
    let bestArea = Infinity;
    let nodes;
    try {
      nodes = allVideos();
    } catch (e) {
      return null;
    }
    for (const v of nodes) {
      const rect = v.getBoundingClientRect();
      const area = rect.width * rect.height;
      if (area <= 0) continue;
      if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) continue;
      if (area < bestArea) {
        bestArea = area;
        best = v;
      }
    }
    return best;
  }

  function onDocumentPointerDown(e) {
    const path = typeof e.composedPath === "function" ? e.composedPath() : [];
    const insideUi =
      path.indexOf(panelEl) > -1 ||
      path.indexOf(btnEl) > -1 ||
      path.indexOf(pipEl) > -1;
    if (insideUi) return;
    if (panelOpen) closePanel();
    /*
     * A tap on the video itself is the clearest "use this one" signal - and the
     * composed path also reaches a <video> that lives inside a shadow root,
     * which the light-DOM query in videoAtPoint cannot see.
     */
    let hit = null;
    for (const node of path) {
      const tag = node && node.tagName;
      if (tag === "VIDEO" || tag === "AUDIO") {
        hit = node;
        break;
      }
    }
    /*
     * Most players (YouTube, Vimeo, news sites...) draw their own controls on
     * top of the <video>, so the tap never reaches the video node itself.
     * A tap inside the video's box means "the user is using this video", so
     * that is what hands it to Nova. The tap also asks for the preference: a
     * frame whose only video is small and paused never hits the poll.
     */
    if (!hit && typeof e.clientX === "number") hit = videoAtPoint(e.clientX, e.clientY);
    if (!hit) return;
    if (btnEl && !btnEl.hidden) showButton();
    if (hit.tagName !== "VIDEO") return;
    frameTouched = true;
    if (!prefs.pip) fetchPrefs();
    if (pipVideo !== hit) bindPip(hit);
    if (pipVideo !== hit) return;
    /* A tap on the video is the user asking for the button back. */
    showPip();
  }

  /* -------------------------- drag -------------------------------- */

  function setupDrag() {
    let dragging = false;
    let moved = false;
    let startX = 0;
    let startY = 0;
    let originLeft = 0;
    let originTop = 0;

    btnEl.addEventListener("pointerdown", function (e) {
      dragging = true;
      moved = false;
      const rect = btnEl.getBoundingClientRect();
      startX = e.clientX;
      startY = e.clientY;
      originLeft = rect.left;
      originTop = rect.top;
      try {
        btnEl.setPointerCapture(e.pointerId);
      } catch (err) {
        /* ignore */
      }
      e.preventDefault();
      e.stopPropagation();
    });

    btnEl.addEventListener("pointermove", function (e) {
      if (!dragging) return;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      if (!moved && Math.abs(dx) + Math.abs(dy) < 8) return;
      moved = true;
      const left = Math.min(Math.max(4, originLeft + dx), window.innerWidth - 46);
      const top = Math.min(Math.max(4, originTop + dy), window.innerHeight - 46);
      pos = { left: left, top: top };
      applyPos();
      positionPanel();
    });

    btnEl.addEventListener("pointerup", function (e) {
      if (!dragging) return;
      dragging = false;
      try {
        btnEl.releasePointerCapture(e.pointerId);
      } catch (err) {
        /* ignore */
      }
      e.preventDefault();
      e.stopPropagation();
      if (!moved) {
        togglePanel();
        return;
      }
      snapToEdge();
      scheduleIdle();
    });

    btnEl.addEventListener("pointercancel", function () {
      dragging = false;
    });

    btnEl.addEventListener("pointerenter", function () {
      showButtonNow();
    });
    btnEl.addEventListener("pointerleave", function () {
      scheduleIdle();
    });
  }

  function applyPos() {
    if (!pos) return;
    btnEl.style.left = pos.left + "px";
    btnEl.style.top = pos.top + "px";
    btnEl.style.right = "auto";
    btnEl.style.bottom = "auto";
  }

  function positionButton() {
    if (!btnEl) return;
    if (!pos) {
      pos = {
        left: Math.max(10, window.innerWidth - 48),
        top: Math.max(10, window.innerHeight - 150),
      };
    }
    pos.left = Math.min(Math.max(4, pos.left), Math.max(4, window.innerWidth - 46));
    pos.top = Math.min(Math.max(4, pos.top), Math.max(4, window.innerHeight - 46));
    applyPos();
  }

  function snapToEdge() {
    const rect = btnEl.getBoundingClientRect();
    const margin = 10;
    const toLeft = rect.left + rect.width / 2 < window.innerWidth / 2;
    const top = Math.min(Math.max(margin, rect.top), window.innerHeight - rect.height - margin);
    const left = toLeft ? margin : window.innerWidth - rect.width - margin;
    pos = { left: left, top: top };
    applyPos();
    positionPanel();
  }

  const AUTO_HIDE_MS = 2500;

  /*
   * The download button is a "peek" affordance: it appears for a couple of
   * seconds and then fades away, and comes back whenever something happens
   * that the user might want it for (a video starts or pauses, the page is
   * tapped, the pointer is resting on it, it was just dragged, ...). It stays
   * put while the picker is open, because the picker is anchored to it.
   */
  function showButtonNow() {
    if (!btnEl) return;
    btnEl.classList.remove("nv-hide");
    clearTimeout(hideTimer);
  }

  function hideIfIdle() {
    if (!btnEl || panelOpen || btnEl.hidden) return;
    let hovering = false;
    try {
      hovering = btnEl.matches(":hover");
    } catch (e) {
      /* ignore */
    }
    if (hovering) {
      scheduleIdle();
      return;
    }
    btnEl.classList.add("nv-hide");
  }

  function scheduleIdle() {
    clearTimeout(hideTimer);
    hideTimer = setTimeout(hideIfIdle, AUTO_HIDE_MS);
  }

  function showButton() {
    showButtonNow();
    scheduleIdle();
  }

  /* -------------------------- panel ------------------------------- */

  function togglePanel() {
    if (panelOpen) closePanel();
    else openPanel();
  }

  function openPanel() {
    if (!panelEl) return;
    panelOpen = true;
    panelEl.hidden = false;
    keepAlive();
    showButtonNow();
    /* The picker draws above the bar; leave the bar where it is. */
    renderList();
    positionPanel();
    requestAnimationFrame(positionPanel);
    refreshEntries(true);
    restoreYtdlpJobs();
  }

  /*
   * Closing the picker only hides the UI. It deliberately does NOT touch
   * ytdlpJobs or the native bridge: a download keeps running in the
   * background and keeps being tracked by the pump. Only the row's "Cancel"
   * button (cancelYtDlp) stops a download.
   */
  function closePanel() {
    panelOpen = false;
    if (panelEl) panelEl.hidden = true;
    scheduleIdle();
  }

  function positionPanel() {
    if (!panelEl || panelEl.hidden || !btnEl) return;
    const margin = 10;
    const rect = btnEl.getBoundingClientRect();
    const panelRect = panelEl.getBoundingClientRect();
    const width = panelRect.width || 300;
    const height = panelRect.height || 260;
    let left = rect.left + rect.width / 2 < window.innerWidth / 2
      ? margin
      : window.innerWidth - width - margin;
    let top = rect.top - height - 8;
    if (top < margin) top = rect.bottom + 8;
    if (top + height > window.innerHeight - margin) {
      top = Math.max(margin, window.innerHeight - height - margin);
    }
    left = Math.min(Math.max(margin, left), Math.max(margin, window.innerWidth - width - margin));
    panelEl.style.left = left + "px";
    panelEl.style.top = top + "px";
    panelEl.style.right = "auto";
    panelEl.style.bottom = "auto";
  }

  /*
   * While the page itself is fullscreen (a site moving its own <video> there)
   * the download button is put away. The entries are untouched, so it comes
   * straight back when fullscreen ends. Nova never touches the video itself.
   */
  function onFullscreenChange() {
    if (!host) return;
    let el = null;
    try {
      el = document.fullscreenElement || document.webkitFullscreenElement || null;
    } catch (e) {
      el = null;
    }
    if (btnEl) btnEl.hidden = !!el || entries.length === 0 || prefs.downloader === false;
    if (el && panelOpen) closePanel();
    if (!el) {
      positionButton();
      positionPanel();
    }
  }

  function checkVisibility() {
    if (!host || !btnEl) return;
    keepAlive();
    const wanted = prefs.downloader !== false && entries.length > 0;
    if (!wanted) {
      if (!btnEl.hidden) {
        btnEl.hidden = true;
        closePanel();
      }
    } else {
      const wasHidden = btnEl.hidden;
      btnEl.hidden = false;
      positionButton();
      /*
       * Only pop the button back up on a real no-media -> media transition.
       * Doing it on every poll would restart the auto-hide timer forever, so
       * the button would never actually disappear.
       */
      if (wasHidden) showButton();
    }
    updatePip();
  }

  function toast(message) {
    if (!shadow || !IS_TOP) return;
    let el = shadow.querySelector(".nv-toast");
    if (!el) {
      el = document.createElement("div");
      el.className = "nv-toast";
      shadow.appendChild(el);
    }
    el.textContent = message;
    el.hidden = false;
    el.style.left = "50%";
    el.style.bottom = "26px";
    el.style.transform = "translateX(-50%)";
    clearTimeout(el.__timer);
    el.__timer = setTimeout(function () {
      el.hidden = true;
    }, 3200);
  }

  /* -------------------------- formatting -------------------------- */

  function fmtBytes(n) {
    if (!n || n < 0) return "";
    const units = ["B", "KB", "MB", "GB"];
    let i = 0;
    let value = n;
    while (value >= 1024 && i < units.length - 1) {
      value /= 1024;
      i++;
    }
    return (value >= 10 || i === 0 ? Math.round(value) : value.toFixed(1)) + " " + units[i];
  }

  function fmtDuration(s) {
    if (!s || !isFinite(s)) return "";
    s = Math.round(s);
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    const pad = function (x) { return (x < 10 ? "0" : "") + x; };
    return h ? h + ":" + pad(m) + ":" + pad(sec) : m + ":" + pad(sec);
  }

  function fileNameOf(url) {
    try {
      const u = new URL(url, location.href);
      const last = u.pathname.split("/").filter(Boolean).pop() || "";
      if (last) return decodeURIComponent(last);
    } catch (e) {
      /* ignore */
    }
    return url.slice(0, 60);
  }

  function kindLabel(kind) {
    if (kind === "hls") return "HLS";
    if (kind === "dash") return "DASH";
    if (kind === "audio") return "Audio";
    if (kind === "ytdlp") return "yt-dlp";
    return "Video";
  }

  /* -------------------------- list -------------------------------- */

  function renderList() {
    if (!listEl) return;
    rowRefs = new Map();
    listEl.innerHTML = "";
    if (!entries.length) {
      const empty = document.createElement("div");
      empty.className = "nv-empty";
      empty.textContent = "No videos found yet. Press play, then tap the button again.";
      listEl.appendChild(empty);
      return;
    }
    for (const entry of entries) {
      listEl.appendChild(buildRow(entry));
    }
  }

  function buildRow(entry) {
    const row = document.createElement("div");
    row.className = "nv-row";

    const top = document.createElement("div");
    top.className = "nv-row-top";

    const kind = document.createElement("span");
    kind.className = "nv-kind nv-" + entry.kind;
    kind.textContent = entry.kind === "ytdlp" ? (entry.site || "yt-dlp") : kindLabel(entry.kind);
    top.appendChild(kind);

    const name = document.createElement("span");
    name.className = "nv-name";
    name.textContent = entry.title || fileNameOf(entry.url);
    name.title = entry.url;
    top.appendChild(name);

    const size = document.createElement("span");
    size.className = "nv-size";
    size.textContent = entry.contentLength ? fmtBytes(entry.contentLength) : "";
    top.appendChild(size);
    row.appendChild(top);

    const meta = document.createElement("div");
    meta.className = "nv-meta";
    const bits = [];
    if (entry.width && entry.height) bits.push(entry.width + "\u00d7" + entry.height);
    if (entry.duration) bits.push(fmtDuration(entry.duration));
    bits.push(entry.url);
    meta.textContent = bits.join("  \u00b7  ");
    row.appendChild(meta);

    const actions = document.createElement("div");
    actions.className = "nv-actions";

    const go = document.createElement("button");
    go.className = "nv-go";
    go.type = "button";
    go.textContent = "Download";
    go.addEventListener("click", function () {
      if (entry.kind === "ytdlp") startYtDlp(entry, audioBox && audioBox.checked);
      else startDownload(entry);
    });
    actions.appendChild(go);

    let cancelBtn = null;
    let audioBox = null;
    if (entry.kind === "ytdlp") {
      cancelBtn = document.createElement("button");
      cancelBtn.className = "nv-copy nv-cancel";
      cancelBtn.type = "button";
      cancelBtn.textContent = "Cancel";
      cancelBtn.hidden = true;
      cancelBtn.addEventListener("click", function () {
        cancelYtDlp(entry);
      });
      actions.appendChild(cancelBtn);

      const audioLabel = document.createElement("label");
      audioLabel.className = "nv-check";
      audioBox = document.createElement("input");
      audioBox.type = "checkbox";
      audioLabel.appendChild(audioBox);
      audioLabel.appendChild(document.createTextNode("Audio only"));
      actions.appendChild(audioLabel);
    }

    const copy = document.createElement("button");
    copy.className = "nv-copy";
    copy.type = "button";
    copy.textContent = "Copy link";
    copy.addEventListener("click", function () {
      copyText(entry.url);
    });
    actions.appendChild(copy);

    let select = null;
    if (entry.kind === "hls" || entry.kind === "dash") {
      select = document.createElement("select");
      select.className = "nv-select";
      actions.appendChild(select);
    }
    row.appendChild(actions);

    const status = document.createElement("div");
    status.className = "nv-status";
    row.appendChild(status);

    const prog = document.createElement("div");
    prog.className = "nv-prog";
    prog.hidden = true;
    const bar = document.createElement("i");
    prog.appendChild(bar);
    row.appendChild(prog);

    rowRefs.set(entry.url, { row: row, status: status, prog: prog, bar: bar, go: go, select: select, cancel: cancelBtn });

    const activeJob = entry.kind === "ytdlp" ? ytdlpJobs.get(entry.url) : null;
    if (activeJob) {
      go.disabled = true;
      if (cancelBtn) cancelBtn.hidden = false;
      status.textContent = activeJob.message || "Downloading\u2026";
      if (activeJob.state === "error") status.className = "nv-status nv-err";
      if (typeof activeJob.progress === "number" && activeJob.progress > 0) {
        prog.hidden = false;
        bar.style.width = Math.max(0, Math.min(100, Math.round(activeJob.progress * 100))) + "%";
      }
    }
    if (entry.kind === "hls") scheduleHlsInfo(entry);
    if (entry.kind === "dash") scheduleDashInfo(entry);

    return row;
  }

  function setStatus(url, message, isError) {
    const ref = rowRefs.get(url);
    if (!ref) return;
    ref.status.textContent = message || "";
    ref.status.className = "nv-status" + (isError ? " nv-err" : "");
  }

  function setProgress(url, ratio) {
    const ref = rowRefs.get(url);
    if (!ref) return;
    if (ratio == null) {
      ref.prog.hidden = true;
      return;
    }
    ref.prog.hidden = false;
    ref.bar.style.width = Math.max(0, Math.min(100, Math.round(ratio * 100))) + "%";
  }

  function setBusy(url, isBusy) {
    const ref = rowRefs.get(url);
    if (ref) ref.go.disabled = isBusy;
  }

  /* -------------------------- HLS / DASH info --------------------- */

  function scheduleHlsInfo(entry) {
    if (hlsInfo.has(entry.url)) {
      applyHlsInfo(entry);
      return;
    }
    setStatus(entry.url, "Reading playlist\u2026");
    send("novaVideo:resolveHls", { url: entry.url }).then(function (res) {
      hlsInfo.set(entry.url, res);
      applyHlsInfo(entry);
    });
  }

  function optionValue(value, label) {
    const opt = document.createElement("option");
    opt.value = value;
    opt.textContent = label;
    return opt;
  }

  function applyHlsInfo(entry) {
    const res = hlsInfo.get(entry.url);
    const ref = rowRefs.get(entry.url);
    if (!res || !ref) return;
    if (!res.ok) {
      setStatus(entry.url, "Playlist error: " + (res.error || "unknown"), true);
      return;
    }
    if (res.type === "master") {
      ref.select.innerHTML = "";
      for (const variant of res.variants) {
        const opt = document.createElement("option");
        opt.value = variant.url;
        opt.textContent = variant.name +
          (variant.bandwidth ? " \u00b7 " + Math.round(variant.bandwidth / 1000) + " kbps" : "");
        ref.select.appendChild(opt);
      }
      setStatus(entry.url, res.variants.length + " qualities available");
    } else {
      ref.select.appendChild(optionValue(entry.url, "Auto (best)"));
      const bits = [res.segments.length + " segments"];
      if (res.duration) bits.push(fmtDuration(res.duration));
      if (res.encrypted) bits.push("encrypted");
      setStatus(entry.url, bits.join(" \u00b7 "));
    }
  }

  function scheduleDashInfo(entry) {
    if (dashInfo.has(entry.url)) {
      applyDashInfo(entry);
      return;
    }
    setStatus(entry.url, "Reading manifest\u2026");
    send("novaVideo:resolveDash", { url: entry.url }).then(function (res) {
      dashInfo.set(entry.url, res);
      applyDashInfo(entry);
    });
  }

  function applyDashInfo(entry) {
    const res = dashInfo.get(entry.url);
    const ref = rowRefs.get(entry.url);
    if (!res || !ref) return;
    if (!res.ok) {
      setStatus(entry.url, "Manifest error: " + (res.error || "unknown"), true);
      return;
    }
    ref.select.innerHTML = "";
    res.reps.forEach(function (rep, index) {
      const opt = document.createElement("option");
      opt.value = String(index);
      const label = [];
      if (rep.height) label.push(rep.height + "p");
      label.push(rep.type === "audio" ? "audio" : "video");
      if (rep.bandwidth) label.push(Math.round(rep.bandwidth / 1000) + " kbps");
      opt.textContent = label.join(" \u00b7 ") + (rep.single ? " (single file)" : "");
      ref.select.appendChild(opt);
    });
    const hasVideo = res.reps.some(function (r) { return r.type === "video"; });
    setStatus(entry.url, hasVideo ? "Video and audio are separate tracks" : "Audio only");
  }

  /* -------------------------- fetching ---------------------------- */

  function base64ToBytes(b64) {
    const binary = atob(b64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
  }

  function fetchBytes(url) {
    return fetch(url, { credentials: "include" })
      .then(function (res) {
        if (!res.ok) throw new Error("HTTP " + res.status);
        return res.arrayBuffer();
      })
      .then(function (buf) {
        return new Uint8Array(buf);
      })
      .catch(function () {
        return send("novaVideo:fetchBinary", { url: url }).then(function (res) {
          if (!res || !res.ok || !res.base64) {
            throw new Error((res && res.error) || "Network request failed");
          }
          return base64ToBytes(res.base64);
        });
      });
  }

  async function downloadSegments(segments, filename, mime, entryUrl) {
    const parts = new Array(segments.length);
    let done = 0;
    let next = 0;
    const workerCount = Math.min(3, segments.length);
    async function worker() {
      for (;;) {
        const index = next++;
        if (index >= segments.length) return;
        parts[index] = await fetchBytes(segments[index]);
        done++;
        setProgress(entryUrl, done / segments.length);
        if (done % 10 === 0 || done === segments.length) {
          const bytes = parts.reduce(function (sum, part) {
            return sum + (part ? part.length : 0);
          }, 0);
          setStatus(entryUrl, "Downloading\u2026 " + done + "/" + segments.length +
            (bytes ? "  (" + fmtBytes(bytes) + ")" : ""));
        }
      }
    }
    await Promise.all(Array.from({ length: workerCount }, worker));
    setProgress(entryUrl, null);
    setStatus(entryUrl, "Assembling file\u2026");
    const blob = new Blob(parts, { type: mime || "application/octet-stream" });
    saveBlob(blob, filename);
  }

  function saveBlob(blob, filename) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.style.display = "none";
    (document.body || document.documentElement).appendChild(link);
    link.click();
    setTimeout(function () {
      link.remove();
      URL.revokeObjectURL(url);
    }, 120000);
  }

  function guessName(entry, ext) {
    let base = "";
    try {
      const u = new URL(entry.url, location.href);
      const last = u.pathname.split("/").filter(Boolean).pop() || "";
      base = decodeURIComponent(last.replace(/\.[a-z0-9]{1,5}$/i, ""));
      if (!base) base = u.hostname.replace(/^www\./, "");
    } catch (e) {
      /* ignore */
    }
    if (!base) base = (document.title || "video").replace(/[^\w\- ]+/g, "").trim().slice(0, 60);
    if (!base) base = "video";
    return base + "." + ext;
  }

  function copyText(text) {
    const done = function () {
      toast("Link copied");
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done).catch(function () {
        fallbackCopy(text, done);
      });
    } else {
      fallbackCopy(text, done);
    }
  }

  function fallbackCopy(text, done) {
    const area = document.createElement("textarea");
    area.value = text;
    area.style.cssText = "position:fixed;opacity:0;";
    (document.body || document.documentElement).appendChild(area);
    area.select();
    try {
      document.execCommand("copy");
      done();
    } catch (e) {
      toast("Copy failed");
    }
    area.remove();
  }

  /* -------------------------- download flow ----------------------- */

  async function startDownload(entry) {
    const ref = rowRefs.get(entry.url);
    if (!ref || ref.go.disabled) return;
    setBusy(entry.url, true);
    setProgress(entry.url, null);
    setStatus(entry.url, "Starting\u2026");
    try {
      if (entry.kind === "hls") {
        await downloadHls(entry, ref.select ? ref.select.value : entry.url);
      } else if (entry.kind === "dash") {
        await downloadDash(entry, ref.select ? parseInt(ref.select.value, 10) || 0 : 0);
      } else {
        await downloadDirect(entry);
      }
      setStatus(entry.url, "Download started. Check your downloads.");
    } catch (e) {
      setStatus(entry.url, "Failed: " + ((e && e.message) || e), true);
    } finally {
      setBusy(entry.url, false);
      setProgress(entry.url, null);
    }
  }

  async function downloadDirect(entry) {
    setStatus(entry.url, "Fetching file\u2026");
    const bytes = await fetchBytes(entry.url);
    const ext = entry.kind === "audio" ? "m4a" : "mp4";
    const blob = new Blob([bytes], {
      type: entry.contentType || (entry.kind === "audio" ? "audio/mpeg" : "video/mp4"),
    });
    saveBlob(blob, guessName(entry, ext));
  }

  async function downloadHls(entry, targetUrl) {
    let resolved = await send("novaVideo:resolveHls", { url: targetUrl });
    if (!resolved || !resolved.ok) {
      hlsInfo.set(entry.url, resolved);
      throw new Error((resolved && resolved.error) || "Could not read playlist");
    }
    if (resolved.type === "master") {
      const best = resolved.variants[0];
      if (!best) throw new Error("No qualities found");
      resolved = await send("novaVideo:resolveHls", { url: best.url });
      if (!resolved || !resolved.ok) {
        throw new Error((resolved && resolved.error) || "Could not read quality playlist");
      }
    }
    if (!resolved.segments || !resolved.segments.length) throw new Error("No segments found");
    const ext = resolved.hasInit ? "mp4" : "ts";
    const mime = resolved.hasInit ? "video/mp4" : "video/mp2t";
    setStatus(entry.url, "Downloading " + resolved.segments.length + " segments\u2026");
    await downloadSegments(resolved.segments, guessName(entry, ext), mime, entry.url);
  }

  async function downloadDash(entry, index) {
    const res = dashInfo.get(entry.url) || (await send("novaVideo:resolveDash", { url: entry.url }));
    if (!res || !res.ok) throw new Error((res && res.error) || "Could not read manifest");
    dashInfo.set(entry.url, res);
    const rep = res.reps[index] || res.reps[0];
    if (!rep) throw new Error("No representation found");
    if (rep.incomplete) throw new Error("Unsupported DASH layout - use Copy link");
    if (rep.single) {
      const bytes = await fetchBytes(rep.segments[0]);
      saveBlob(
        new Blob([bytes], { type: rep.mime || "video/mp4" }),
        guessName(entry, rep.type === "audio" ? "m4a" : "mp4"),
      );
      return;
    }
    const ext = rep.type === "audio" ? "m4a" : "mp4";
    const mime = rep.mime || (rep.type === "audio" ? "audio/mp4" : "video/mp4");
    setStatus(entry.url, "Downloading " + rep.segments.length + " segments\u2026");
    await downloadSegments(rep.segments, guessName(entry, ext), mime, entry.url);
  }

  /* -------------------------- yt-dlp (native) --------------------- */

  const NATIVE_TIMEOUT_MS = 6000;
  /* How long a job may sit unbound before we call the bridge unreachable. The
   * first yt-dlp start can take a while (the binaries get extracted). */
  const YTDLP_START_GRACE_MS = 120000;
  let ytdlpPump = null;
  let ytdlpListBusy = false;

  /*
   * `send` never resolves if the native bridge hangs (e.g. the Kotlin side is
   * still registering). Race every yt-dlp call against a timeout so the row can
   * never sit on "Starting yt-dlp..." forever.
   */
  function sendYtDlp(extra) {
    return Promise.race([
      send("novaVideo:ytdlp", extra),
      new Promise(function (resolve) {
        setTimeout(function () {
          resolve({ ok: false, timeout: true, error: "The downloader took too long to respond." });
        }, NATIVE_TIMEOUT_MS);
      }),
    ]).then(function (res) {
      return res || { ok: false, error: "no response" };
    });
  }

  function activeYtdlpJobs() {
    const out = [];
    ytdlpJobs.forEach(function (job) {
      if (job && job.id) out.push(job);
    });
    return out;
  }

  function applyJobToRow(url, job) {
    const ref = rowRefs.get(url);
    if (!ref) return;
    ref.go.disabled = true;
    if (ref.cancel) ref.cancel.hidden = false;
    if (typeof job.progress === "number" && job.progress > 0) setProgress(url, job.progress);
    setStatus(url, job.message || "Downloading\u2026", job.state === "error");
  }

  function finishYtDlp(url) {
    ytdlpJobs.delete(url);
    const ref = rowRefs.get(url);
    if (ref) {
      ref.go.disabled = false;
      if (ref.cancel) ref.cancel.hidden = true;
    }
    stopPumpIfIdle();
  }

  function startPump() {
    if (ytdlpPump) return;
    ytdlpPump = setInterval(pumpYtDlp, 1000);
    pumpYtDlp();
  }

  function stopPumpIfIdle() {
    if (ytdlpPump && ytdlpJobs.size === 0) {
      clearInterval(ytdlpPump);
      ytdlpPump = null;
    }
  }

  function bindNativeJob(job, nativeJob) {
    if (!job || !nativeJob || !nativeJob.id) return;
    job.id = nativeJob.id;
    job.state = nativeJob.state || "running";
    if (typeof nativeJob.progress === "number") job.progress = nativeJob.progress;
    if (nativeJob.message) job.message = nativeJob.message;
    if (nativeJob.filename) job.filename = nativeJob.filename;
    if (nativeJob.error) job.error = nativeJob.error;
    applyJobToRow(job.url, job);
    if (job.cancelRequested) send("novaVideo:ytdlp", { action: "cancel", id: job.id });
  }

  /*
   * Polls the native bridge on its own timer - NOT tied to the panel being
   * open. Closing the picker (or the whole tab) must not cancel anything, and
   * this is also what keeps the progress text updating while it is open.
   *
   * Jobs that have not been bound to a native id yet are matched against the
   * native job list by page URL. That way a slow first "start" reply (the
   * Kotlin bridge warming up / extracting yt-dlp) is recovered automatically
   * instead of leaving the row stuck on "Waiting for the downloader".
   */
  function pumpYtDlp() {
    if (contextDead) {
      stopPumpIfIdle();
      return;
    }
    if (!ytdlpJobs.size) {
      stopPumpIfIdle();
      return;
    }

    const pending = [];
    ytdlpJobs.forEach(function (job) {
      if (job && !job.id) pending.push(job);
    });

    if (!pending.length) {
      pollActiveYtdlpJobs();
      return;
    }

    if (ytdlpListBusy) return;
    ytdlpListBusy = true;
    sendYtDlp({ action: "list" }).then(function (res) {
      ytdlpListBusy = false;
      if (contextDead) return;
      if (res && res.ok && Array.isArray(res.jobs)) {
        for (const nativeJob of res.jobs) {
          if (!nativeJob || !nativeJob.id || !nativeJob.url) continue;
          const local = ytdlpJobs.get(nativeJob.url);
          if (local && !local.id) bindNativeJob(local, nativeJob);
        }
      }
      for (const job of pending) {
        if (job.id) continue;
        const age = Date.now() - (job.startedAt || 0);
        if (age > YTDLP_START_GRACE_MS) {
          job.state = "error";
          job.message = job.cancelRequested
            ? "Canceled"
            : "The downloader is not responding. Try again.";
          applyJobToRow(job.url, job);
          finishYtDlp(job.url);
        } else if (job.cancelRequested) {
          setStatus(job.url, "Canceling\u2026");
        } else {
          job.message = "Waiting for the downloader\u2026";
          setStatus(job.url, job.message);
        }
      }
      pollActiveYtdlpJobs();
    });
  }

  function pollActiveYtdlpJobs() {
    activeYtdlpJobs().forEach(pollOneYtdlpJob);
  }

  function pollOneYtdlpJob(job) {
    send("novaVideo:ytdlp", { action: "status", id: job.id }).then(function (res) {
      const current = ytdlpJobs.get(job.url);
      if (!current || current.id !== job.id) return;
      if (!res) return;
      if (!res.ok) {
        current.state = "error";
        current.message = res.error || "Lost track of the download";
        applyJobToRow(job.url, current);
        finishYtDlp(job.url);
        return;
      }
      current.state = res.state || "running";
      if (typeof res.progress === "number") current.progress = res.progress;
      if (res.message) current.message = res.message;
      if (res.filename) current.filename = res.filename;
      if (res.error) current.error = res.error;
      if (current.state === "running") {
        applyJobToRow(job.url, current);
        return;
      }
      if (current.state === "done") {
        setProgress(job.url, 1);
        current.message = res.message || ("Saved " + (res.filename || ""));
        applyJobToRow(job.url, current);
        toast("Download complete: " + (res.filename || ""));
        finishYtDlp(job.url);
        return;
      }
      if (current.state === "canceled") {
        setProgress(job.url, null);
        current.message = "Canceled";
        applyJobToRow(job.url, current);
        finishYtDlp(job.url);
        return;
      }
      current.message = res.error || current.message || "Download failed";
      applyJobToRow(job.url, current);
      finishYtDlp(job.url);
    });
  }

  /*
   * Re-attach to downloads the native bridge is still running. Needed after a
   * page reload, and when the picker is reopened: the job lives in Kotlin, not
   * in this frame.
   */
  function restoreYtdlpJobs() {
    if (contextDead) return;
    sendYtDlp({ action: "list" }).then(function (res) {
      if (!res || !res.ok || !Array.isArray(res.jobs)) return;
      let added = false;
      for (const nativeJob of res.jobs) {
        if (!nativeJob || !nativeJob.id || !nativeJob.url) continue;
        const existing = ytdlpJobs.get(nativeJob.url);
        if (existing) {
          if (!existing.id) bindNativeJob(existing, nativeJob);
          continue;
        }
        ytdlpJobs.set(nativeJob.url, {
          id: nativeJob.id,
          url: nativeJob.url,
          state: nativeJob.state || "running",
          progress: typeof nativeJob.progress === "number" ? nativeJob.progress : 0,
          message: nativeJob.message || "Downloading\u2026",
          filename: nativeJob.filename || "",
          error: nativeJob.error || null,
          startedAt: Date.now(),
          cancelRequested: false,
        });
        added = true;
      }
      if (added || ytdlpJobs.size) {
        startPump();
        if (panelOpen) renderList();
      }
    });
  }

  /*
   * Start a download. The native bridge owns the job, so the row is created
   * locally first and the pump binds it to the native id as soon as the bridge
   * reports it - a slow first reply therefore shows "Waiting for the
   * downloader..." briefly instead of failing, and then switches to real
   * progress.
   */
  async function startYtDlp(entry, audioOnly) {
    const ref = rowRefs.get(entry.url);
    const existing = ytdlpJobs.get(entry.url);
    if (existing && existing.id) return;

    setBusy(entry.url, true);
    setProgress(entry.url, null);
    if (ref && ref.cancel) ref.cancel.hidden = false;

    const job = existing || { id: null, url: entry.url };
    job.state = "starting";
    job.progress = 0;
    job.message = "Starting yt-dlp\u2026";
    job.filename = "";
    job.error = null;
    job.startedAt = Date.now();
    job.cancelRequested = false;
    ytdlpJobs.set(entry.url, job);
    setStatus(entry.url, job.message);
    startPump();

    const res = await sendYtDlp({ action: "start", url: entry.url, audioOnly: !!audioOnly });
    if (res && res.ok && res.id) {
      bindNativeJob(job, res);
      return;
    }
    if (!res || res.timeout) {
      /* Not a failure: the bridge is just slow to answer. Leave the job
       * pending so the pump can bind it from the native list. */
      if (!job.id) {
        job.message = "Waiting for the downloader\u2026";
        setStatus(entry.url, job.message);
      }
      return;
    }

    /* A definitive failure (no bridge at all, bad URL, ...). */
    ytdlpJobs.delete(entry.url);
    stopPumpIfIdle();
    setBusy(entry.url, false);
    if (ref && ref.cancel) ref.cancel.hidden = true;
    setStatus(entry.url, res.error || "yt-dlp is not available on this device.", true);
  }

  function cancelYtDlp(entry) {
    const job = ytdlpJobs.get(entry.url);
    if (!job) return;
    job.cancelRequested = true;
    setStatus(entry.url, "Canceling\u2026");
    if (!job.id) {
      /* The bridge still owes us an id; bindNativeJob sends the cancel when it
       * arrives. If it never does, the pump times the job out. */
      job.message = "Canceling\u2026";
      applyJobToRow(entry.url, job);
      return;
    }
    send("novaVideo:ytdlp", { action: "cancel", id: job.id });
  }

  /* ---------------------------------------------------------------- */
  /* PIP mode (browser-menu switch "Enable PIP mode")                 */
  /*                                                                   */
  /* The whole feature is one small button over the page's <video>. It  */
  /* peeks for three seconds when the video starts, when it is paused   */
  /* and when it is tapped - exactly the peek the download button uses  */
  /* - and hides itself again. Tapping it puts the video into the       */
  /* browser's picture-in-picture window (or takes it back out). Nova   */
  /* never moves or restyles the video, so the site's own player keeps  */
  /* working exactly as before.                                         */
  /* ---------------------------------------------------------------- */

  /* ~200x200. Paused videos smaller than this are thumbnails/decoration. */
  const MIN_MEDIA_AREA = 40000;
  /* How long the button lingers after a play, a pause, a seek or a tap. */
  const PIP_SHOW_MS = 3000;
  const MEDIA_EVENTS = [
    "play", "playing", "pause", "seeked", "loadedmetadata",
    "enterpictureinpicture", "leavepictureinpicture",
  ];

  const PIP_ICON =
    '<svg viewBox="0 0 24 24"><path d="M3 4.2h18c.66 0 1.2.54 1.2 1.2v13.2c0 .66-.54 1.2-1.2 1.2H3c-.66 0-1.2-.54-1.2-1.2V5.4c0-.66.54-1.2 1.2-1.2zm1.2 2.4v10.8h15.6V6.6H4.2z"/>' +
    '<path d="M12.5 11.6h6.1v4.9h-6.1z"/></svg>';

  /*
   * The two feature switches live in Android shared preferences, so they are
   * read through the background script (which owns the native bridge). Polled
   * lazily: on boot, when the page regains focus, and every few seconds, so a
   * menu toggle is picked up without the user having to reload the page.
   */
  function fetchPrefs() {
    if (contextDead || prefsBusy) return;
    prefsBusy = true;
    send("novaVideo:prefs", {}).then(function (res) {
      prefsBusy = false;
      if (contextDead) return;
      if (!res || !res.ok) return;
      const nextPip = !!res.pip;
      const nextDownloader = res.downloader !== false;
      const wasPip = prefs.pip;
      const changed = nextPip !== prefs.pip || nextDownloader !== prefs.downloader;
      prefs.pip = nextPip;
      prefs.downloader = nextDownloader;
      if (changed) applyPrefs();
      if (prefsPrimed && nextPip !== wasPip) {
        toast(nextPip ? "PIP mode on" : "PIP mode off");
      }
      prefsPrimed = true;
    });
  }

  function applyPrefs() {
    if (btnEl && prefs.downloader === false && !btnEl.hidden) {
      btnEl.hidden = true;
      closePanel();
    }
    if (!prefs.pip) {
      hidePip();
      if (pipEl) pipEl.hidden = true;
    }
    updatePip();
  }

  /* ------------------------- pip button --------------------------- */

  function ensurePip() {
    if (pipEl) return pipEl;
    if (!shadow) buildHost();
    if (!shadow) return null;
    pipEl = document.createElement("div");
    pipEl.className = "nv-pip nv-hide";
    pipEl.hidden = true;
    pipEl.title = "Picture in picture";
    pipEl.setAttribute("role", "button");
    pipEl.setAttribute("aria-label", "Picture in picture");
    pipEl.innerHTML = PIP_ICON;
    /*
     * Act on pointerup rather than click so the browser still sees a real user
     * gesture: requestPictureInPicture refuses to run without one.
     */
    pipEl.addEventListener("pointerdown", function (e) {
      e.stopPropagation();
      e.preventDefault();
      showPip();
    });
    pipEl.addEventListener("pointerup", function (e) {
      e.stopPropagation();
      e.preventDefault();
      showPip();
      togglePip();
    });
    pipEl.addEventListener("click", function (e) {
      e.preventDefault();
      e.stopPropagation();
    });
    shadow.appendChild(pipEl);
    keepAlive();
    return pipEl;
  }

  function destroyPip() {
    clearTimeout(pipTimer);
    unbindPip();
    if (pipEl && pipEl.parentNode) pipEl.parentNode.removeChild(pipEl);
    pipEl = null;
  }

  /*
   * The peek: three seconds on screen after a play, a pause, a seek or a tap
   * on the video, then faded out again - the same behaviour as the download
   * button. The next play/pause/tap brings it straight back.
   */
  function showPip() {
    if (!prefs.pip) return;
    const el = ensurePip();
    if (!el) return;
    pipUntil = Date.now() + PIP_SHOW_MS;
    clearTimeout(pipTimer);
    el.hidden = false;
    el.classList.remove("nv-hide");
    positionPip();
    keepAlive();
    pipTimer = setTimeout(hidePip, PIP_SHOW_MS);
  }

  /* Fades the button out. It stays in the DOM, ready for the next peek. */
  function hidePip() {
    clearTimeout(pipTimer);
    if (pipEl) pipEl.classList.add("nv-hide");
  }

  /* Top-left corner of the video it belongs to, clamped to the viewport. */
  function positionPip() {
    if (!pipEl || pipEl.hidden || !pipVideo) return;
    const r = pipVideo.getBoundingClientRect();
    const size = 38;
    const margin = 10;
    const bad =
      r.width < 140 || r.height < 80 ||
      r.right < 30 || r.left > window.innerWidth - 30 ||
      r.bottom < 30 || r.top > window.innerHeight - 30;
    if (bad) {
      hidePip();
      return;
    }
    const left = Math.min(Math.max(4, r.left + margin), Math.max(4, window.innerWidth - size - 4));
    const top = Math.min(Math.max(4, r.top + margin), Math.max(4, window.innerHeight - size - 4));
    pipEl.style.left = left + "px";
    pipEl.style.top = top + "px";
  }

  /*
   * The site's own picture-in-picture support does the real work; all Nova
   * does is ask for it (and take it back down on a second tap). Everything is
   * guarded because the API is simply absent on some pages/devices.
   */
  function togglePip() {
    const v = pipVideo || selectVideo();
    if (!v) {
      toast("No video to show in picture-in-picture");
      return;
    }
    let active = null;
    try {
      active = document.pictureInPictureElement || null;
    } catch (e) {
      active = null;
    }
    if (active === v) {
      try {
        if (document.exitPictureInPicture) document.exitPictureInPicture();
      } catch (e) {
        /* ignore */
      }
      return;
    }
    const hasApi =
      typeof v.requestPictureInPicture === "function" &&
      document.pictureInPictureEnabled !== false;
    if (!hasApi) {
      /* Nova's engine does not expose the page-facing picture-in-picture API,
       * so the browser floats its own window instead (the native bridge). */
      requestNativePip(v);
      return;
    }
    if (v.disablePictureInPicture === true) {
      toast("This video blocks picture-in-picture");
      return;
    }
    try {
      const promise = v.requestPictureInPicture();
      if (promise && promise.catch) {
        promise.catch(function (err) {
          const name = (err && err.name) || "";
          if (name === "NotAllowedError") toast("Picture-in-picture was blocked");
          else if (name === "InvalidStateError") toast("The video isn't ready yet");
          else toast("Could not open picture-in-picture");
        });
      }
    } catch (e) {
      toast("Could not open picture-in-picture");
    }
  }

  /*
   * The native half of PIP mode: the browser puts its own window into Android's
   * picture-in-picture, sized to this video. Used whenever the page has no PiP
   * API of its own.
   */
  function requestNativePip(v) {
    send("novaVideo:pip", {
      width: v.videoWidth || 0,
      height: v.videoHeight || 0,
    }).then(function (res) {
      if (!res || !res.ok) toast("Picture-in-picture isn't available here");
    });
  }

  /* ------------------------- video binding ------------------------ */

  /*
   * Every <video> the frame can reach, including ones inside open shadow roots.
   * `document.querySelectorAll("video")` does not pierce shadow boundaries, and
   * a lot of modern players keep the video in one. The shadow walk is throttled
   * and capped because it is O(elements); the cheap light-DOM query runs always.
   */
  let shadowScanAt = 0;
  function allVideos() {
    const out = [];
    try {
      for (const v of document.querySelectorAll("video")) out.push(v);
    } catch (e) {
      /* ignore */
    }
    const now = Date.now();
    if (now - shadowScanAt < 2400) return out;
    shadowScanAt = now;
    try {
      const nodes = document.querySelectorAll("*");
      const limit = Math.min(nodes.length, 8000);
      for (let i = 0; i < limit; i++) {
        const root = nodes[i].shadowRoot;
        if (!root) continue;
        for (const v of root.querySelectorAll("video")) out.push(v);
      }
    } catch (e) {
      /* ignore */
    }
    return out;
  }

  /*
   * Whether a video is worth a button. Anything that is playing qualifies, and
   * so does anything big enough to be a real player; a video the user taps is
   * always allowed in (see onDocumentPointerDown). A small paused video (a
   * thumbnail or decorative loop) is left alone until then.
   */
  function plausibleVideo(v) {
    if (!v) return false;
    try {
      if (!(v.currentSrc || v.src || v.querySelector("source[src]"))) return false;
    } catch (e) {
      return false;
    }
    if (!v.paused && !v.ended) return true;
    const rect = v.getBoundingClientRect();
    return Math.max(0, rect.width) * Math.max(0, rect.height) >= MIN_MEDIA_AREA;
  }

  function selectVideo() {
    let best = null;
    let bestScore = -1;
    let nodes;
    try {
      nodes = allVideos();
    } catch (e) {
      return null;
    }
    for (const v of nodes) {
      if (!plausibleVideo(v)) continue;
      const rect = v.getBoundingClientRect();
      let score = Math.max(0, rect.width) * Math.max(0, rect.height);
      if (!v.paused && !v.ended) score += 1e9;
      if (isFinite(v.duration) && v.duration > 0) score += 1e6;
      if (score > bestScore) {
        bestScore = score;
        best = v;
      }
    }
    return best;
  }

  function unbindPip() {
    if (pipVideo) {
      for (const pair of pipMediaBound) {
        try {
          pipVideo.removeEventListener(pair[0], pair[1], true);
        } catch (e) {
          /* ignore */
        }
      }
    }
    pipMediaBound = [];
    pipVideo = null;
  }

  function bindPip(v) {
    if (pipVideo === v) return;
    unbindPip();
    pipVideo = v;
    if (!v) {
      if (pipEl) pipEl.hidden = true;
      return;
    }
    const bound = function (e) {
      onPipMedia(e);
    };
    for (const evt of MEDIA_EVENTS) {
      v.addEventListener(evt, bound, true);
      pipMediaBound.push([evt, bound]);
    }
  }

  /*
   * The media element behind an event. `e.target` is retargeted to the shadow
   * host for a <video> that lives inside a shadow root (which is how a growing
   * number of players are built), so walk the composed path instead. Without
   * this, Nova never sees those videos.
   */
  function mediaFromEvent(e) {
    try {
      if (typeof e.composedPath === "function") {
        for (const node of e.composedPath()) {
          const tag = node && node.tagName;
          if (tag === "VIDEO") return node;
        }
      }
    } catch (err) {
      /* fall through */
    }
    const t = e.target;
    if (t && t.tagName === "VIDEO") return t;
    return null;
  }

  /*
   * The top frame always owns the button. Inside an iframe it would fight with
   * every other embedded player on the page, so there it only appears for a
   * video that is actually playing - or one the user has tapped.
   */
  function frameAllows(v) {
    if (IS_TOP) return true;
    if (!v) return false;
    if (!v.paused && !v.ended) return true;
    return frameTouched;
  }

  function onPipMedia(e) {
    if (!prefs.pip) return;
    const el = mediaFromEvent(e);
    if (!el) return;
    if (pipVideo !== el) {
      if (!plausibleVideo(el)) return;
      bindPip(el);
    }
    showPip();
  }

  /*
   * The poll never opens the peek itself (that is the media events and the
   * tap): it only keeps the button on the right video and hides it when there
   * is nothing to act on.
   */
  function updatePip() {
    if (!prefs.pip) {
      hidePip();
      if (pipEl) pipEl.hidden = true;
      unbindPip();
      return;
    }
    const v = pipVideo && pipVideo.isConnected ? pipVideo : selectVideo();
    if (v !== pipVideo) {
      bindPip(v);
      if (v && !v.paused && !v.ended) showPip();
      return;
    }
    if (!pipVideo || !frameAllows(pipVideo)) {
      if (pipEl) pipEl.hidden = true;
      return;
    }
    if (pipEl && !pipEl.hidden) positionPip();
  }

  function onViewportChange() {
    if (pipEl && !pipEl.hidden) positionPip();
  }

  /* -------------------------- entry polling ----------------------- */

  function refreshEntries(force) {
    if (!host || contextDead) return;
    heartbeat();
    send("novaVideo:get", { pageUrl: location.href, pageTitle: document.title || "" }).then(function (res) {
      if (!host) return;
      const next = (res && res.ok && Array.isArray(res.entries)) ? res.entries : [];
      const changed = next.length !== entries.length ||
        next.some(function (entry, i) {
          return !entries[i] || entries[i].url !== entry.url;
        });
      entries = next;
      checkVisibility();
      if (changed && entries.length && prefs.downloader !== false && btnEl && !btnEl.hidden) {
        showButton();
      }
      if (panelOpen && (changed || force)) renderList();
    });
  }

  /* -------------------------- boot -------------------------------- */

  function boot() {
    buildUi();
    refreshEntries(true);
    restoreYtdlpJobs();
    fetchPrefs();
    pollTimer = setInterval(refreshEntries, 1200);
    prefsTimer = setInterval(fetchPrefs, 2000);
    document.addEventListener("visibilitychange", function () {
      if (!document.hidden) fetchPrefs();
    });
    window.addEventListener("focus", function () {
      fetchPrefs();
    });
  }

  /*
   * Videos that live inside an iframe (most embedded players) are invisible to
   * the top frame, so the PiP button also runs in any frame that owns one. The
   * download button stays a top-frame affordance (see buildUi).
   *
   * An iframe with no <video> in it (ads, trackers, widgets) costs nothing but
   * one cheap query per tick: no host, no shadow root, no native round-trips.
   */
  /*
   * One document-level listener set per frame. Binding it is what lets a play,
   * a pause or a tap on a video reach Nova at all, so it is installed as soon
   * as a frame proves it can contain media (see ensureListenersIfVideo).
   */
  function ensureListeners() {
    if (listenersBound) return;
    listenersBound = true;
    document.addEventListener("pointerdown", onDocumentPointerDown, true);
    for (const evt of MEDIA_EVENTS) {
      document.addEventListener(evt, onPipMedia, true);
    }
    document.addEventListener("fullscreenchange", onFullscreenChange, true);
    document.addEventListener("webkitfullscreenchange", onFullscreenChange, true);
    document.addEventListener("scroll", onViewportChange, true);
    window.addEventListener("resize", onViewportChange);
  }

  /*
   * A frame with no <video> at all (ads, trackers, widgets) stays free of Nova's
   * listeners. A frame that owns even a small paused video still needs the tap
   * listener, because that tap is what brings the button up for it.
   */
  function ensureListenersIfVideo() {
    if (listenersBound) return;
    let hasVideo = false;
    try {
      hasVideo = !!document.querySelector("video");
    } catch (e) {
      hasVideo = false;
    }
    if (hasVideo) ensureListeners();
  }

  function bootFramePlayer() {
    ensureListenersIfVideo();
    frameTimer = setInterval(frameTick, 1200);
    frameTick();
  }

  /*
   * Frames only need the button-side of the feature: keep it on the video it
   * belongs to and keep the switches up to date. An iframe with no <video>
   * costs one cheap query per tick.
   */
  function frameTick() {
    if (contextDead) return;
    ensureListenersIfVideo();
    const now = Date.now();
    if (now - framePrefsAt > 2000) {
      framePrefsAt = now;
      fetchPrefs();
    }
    updatePip();
  }

  function start() {
    if (IS_TOP) boot();
    else bootFramePlayer();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start, { once: true });
  } else {
    start();
  }
})();
