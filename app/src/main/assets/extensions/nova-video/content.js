"use strict";

/*
 * Nova Video Downloader - content script.
 *
 * Every frame reports the <video>/<audio> elements it can see to the
 * background script. The top frame additionally renders a small floating
 * button (only when media is detected) that opens the download picker.
 */

(function () {
  if (window.__novaVideoInjected) return;
  window.__novaVideoInjected = true;

  const IS_TOP = (() => {
    try {
      return window.top === window;
    } catch (e) {
      return false;
    }
  })();

  function send(type, extra) {
    return browser.runtime
      .sendMessage(Object.assign({ type: type }, extra || {}))
      .catch(function (e) {
        return { ok: false, error: String((e && e.message) || e) };
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
    let elements;
    try {
      elements = collectElements();
    } catch (e) {
      return;
    }
    if (!elements.length) return;
    send("novaVideo:reportElements", { elements: elements, title: document.title || "" });
  }

  reportElements();
  setInterval(reportElements, 4000);
  document.addEventListener("loadedmetadata", reportElements, true);
  document.addEventListener("play", reportElements, true);
  document.addEventListener("loadeddata", reportElements, true);

  if (!IS_TOP) return;

  /* ---------------------------------------------------------------- */
  /* Top frame: UI                                                     */
  /* ---------------------------------------------------------------- */

  const PREFS_KEY = "novaVideoPrefs";
  const POS_KEY = "novaVideoBtnPos";

  let prefs = { floatEnabled: true, hiddenHosts: [] };
  let entries = [];
  let panelOpen = false;
  let host = null;
  let shadow = null;
  let btnEl = null;
  let panelEl = null;
  let listEl = null;
  let toastEl = null;
  let rowRefs = new Map();
  let hlsInfo = new Map();
  let dashInfo = new Map();
  let busy = new Set();
  let idleTimer = null;

  const CSS = `
    :host { all: initial; }
    * { box-sizing: border-box; }
    .nv-btn {
      position: fixed; width: 46px; height: 46px; border-radius: 50%;
      background: linear-gradient(145deg, #6d5efc, #4b3df0);
      box-shadow: 0 4px 14px rgba(0,0,0,.45);
      display: flex; align-items: center; justify-content: center;
      cursor: pointer; touch-action: none; z-index: 2147483647;
      transition: opacity .25s ease, transform .15s ease;
      opacity: 1;
    }
    .nv-btn.nv-idle { opacity: .32; }
    .nv-btn:active { transform: scale(.94); }
    .nv-btn svg { width: 22px; height: 22px; fill: #fff; pointer-events: none; }
    .nv-badge {
      position: absolute; top: -4px; right: -4px; min-width: 18px; height: 18px;
      padding: 0 4px; border-radius: 9px; background: #ff4d6d; color: #fff;
      font: 600 11px/18px -apple-system, system-ui, sans-serif; text-align: center;
      pointer-events: none;
    }
    .nv-panel {
      position: fixed; width: 330px; max-width: calc(100vw - 20px);
      max-height: 62vh; background: #1b1c21; color: #e9e9ef;
      border: 1px solid #34363f; border-radius: 14px; overflow: hidden;
      box-shadow: 0 12px 40px rgba(0,0,0,.55);
      font: 13px/1.45 -apple-system, system-ui, "Segoe UI", Roboto, sans-serif;
      z-index: 2147483647; display: flex; flex-direction: column;
    }
    .nv-head {
      display: flex; align-items: center; gap: 8px;
      padding: 11px 12px; border-bottom: 1px solid #2b2d35;
      background: #202127;
    }
    .nv-head .nv-title { flex: 1; font-weight: 600; font-size: 13.5px; }
    .nv-x {
      width: 26px; height: 26px; border: 0; border-radius: 8px; cursor: pointer;
      background: #2c2e37; color: #cfd0d8; font-size: 16px; line-height: 1;
    }
    .nv-x:hover { background: #3a3c47; }
    .nv-list { overflow-y: auto; padding: 6px; flex: 1; }
    .nv-empty { padding: 18px 12px; color: #9a9cab; text-align: center; }
    .nv-row { padding: 9px 10px; border-radius: 10px; }
    .nv-row + .nv-row { margin-top: 4px; }
    .nv-row:hover { background: #23242b; }
    .nv-row-top { display: flex; align-items: center; gap: 7px; }
    .nv-kind {
      flex: none; font-size: 10px; font-weight: 700; letter-spacing: .04em;
      text-transform: uppercase; padding: 2px 6px; border-radius: 6px;
      background: #33364a; color: #a9b4ff;
    }
    .nv-kind.nv-audio { background: #2f3a33; color: #8fe0a8; }
    .nv-kind.nv-hls, .nv-kind.nv-dash { background: #3c3231; color: #ffb184; }
    .nv-name {
      flex: 1; min-width: 0; white-space: nowrap; overflow: hidden;
      text-overflow: ellipsis; font-size: 12.5px;
    }
    .nv-size { flex: none; color: #8d8fa0; font-size: 11.5px; }
    .nv-meta { color: #8d8fa0; font-size: 11.5px; margin-top: 3px; word-break: break-all; }
    .nv-actions { display: flex; align-items: center; gap: 6px; margin-top: 7px; flex-wrap: wrap; }
    .nv-go {
      border: 0; border-radius: 8px; padding: 6px 12px; cursor: pointer;
      background: #5847f5; color: #fff; font-size: 12px; font-weight: 600;
    }
    .nv-go:hover { background: #6a5bff; }
    .nv-go[disabled] { opacity: .5; cursor: default; }
    .nv-copy {
      border: 0; border-radius: 8px; padding: 6px 10px; cursor: pointer;
      background: #2c2e37; color: #cfd0d8; font-size: 12px;
    }
    .nv-copy:hover { background: #3a3c47; }
    .nv-select {
      background: #2c2e37; color: #e9e9ef; border: 1px solid #3a3c47;
      border-radius: 8px; padding: 5px 7px; font-size: 12px; max-width: 160px;
    }
    .nv-status { font-size: 11.5px; color: #8d8fa0; margin-top: 6px; }
    .nv-status.nv-err { color: #ff8f9f; }
    .nv-prog {
      height: 4px; border-radius: 2px; background: #2c2e37; margin-top: 7px; overflow: hidden;
    }
    .nv-prog > i { display: block; height: 100%; width: 0; background: #5847f5; transition: width .15s; }
    .nv-foot {
      display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
      padding: 9px 11px; border-top: 1px solid #2b2d35; background: #202127;
      font-size: 11.5px; color: #b7b9c6;
    }
    .nv-foot button {
      border: 0; border-radius: 8px; padding: 5px 9px; cursor: pointer;
      background: #2c2e37; color: #cfd0d8; font-size: 11.5px;
    }
    .nv-foot button:hover { background: #3a3c47; }
    .nv-foot label { display: flex; align-items: center; gap: 4px; margin-left: auto; }
    .nv-toast {
      position: fixed; max-width: 300px; background: #26272e; color: #e9e9ef;
      border: 1px solid #3a3c47; border-radius: 10px; padding: 9px 12px;
      font: 12px/1.4 -apple-system, system-ui, sans-serif; z-index: 2147483647;
      box-shadow: 0 8px 24px rgba(0,0,0,.5);
    }
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

  const ICON =
    '<svg viewBox="0 0 24 24"><path d="M12 16.5l-5.5-5.5h3.25V3h4.5v8H17.5L12 16.5zM5 18h14v2.5H5V18z"/></svg>';

  function buildUi() {
    host = document.createElement("div");
    host.id = "nova-video-host";
    host.style.cssText = "all:initial;position:fixed;top:0;left:0;width:0;height:0;z-index:2147483647;";
    shadow = host.attachShadow({ mode: "open" });
    applyStyles(shadow);

    btnEl = document.createElement("div");
    btnEl.className = "nv-btn";
    btnEl.title = "Download video";
    btnEl.innerHTML = ICON + '<span class="nv-badge" hidden></span>';
    btnEl.hidden = true;

    panelEl = document.createElement("div");
    panelEl.className = "nv-panel";
    panelEl.hidden = true;
    panelEl.innerHTML =
      '<div class="nv-head"><span class="nv-title">Video Downloader</span>' +
      '<button class="nv-x" title="Close">\u00d7</button></div>' +
      '<div class="nv-list"></div>' +
      '<div class="nv-foot"><button class="nv-rescan">Rescan</button>' +
      '<label><input type="checkbox" class="nv-float"> Floating button</label>' +
      '<button class="nv-hide">Hide here</button></div>';

    toastEl = document.createElement("div");
    toastEl.className = "nv-toast";
    toastEl.hidden = true;

    shadow.appendChild(btnEl);
    shadow.appendChild(panelEl);
    shadow.appendChild(toastEl);
    listEl = panelEl.querySelector(".nv-list");

    const floatCheck = panelEl.querySelector(".nv-float");
    floatCheck.checked = prefs.floatEnabled;
    floatCheck.addEventListener("change", function () {
      prefs.floatEnabled = floatCheck.checked;
      savePrefs();
      checkVisibility();
    });
    panelEl.querySelector(".nv-x").addEventListener("click", closePanel);
    panelEl.querySelector(".nv-hide").addEventListener("click", function () {
      const hostName = location.hostname;
      if (hostName && prefs.hiddenHosts.indexOf(hostName) < 0) {
        prefs.hiddenHosts.push(hostName);
        savePrefs();
      }
      closePanel();
      checkVisibility();
      toast("Hidden on " + hostName + ". Use the extension to re-enable.");
    });
    panelEl.querySelector(".nv-rescan").addEventListener("click", function () {
      hlsInfo = new Map();
      dashInfo = new Map();
      refreshEntries(true);
    });

    (document.body || document.documentElement).appendChild(host);
    setupDrag();
    document.addEventListener("fullscreenchange", onFullscreenChange, true);
    window.addEventListener("resize", positionPanel);
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
    });

    btnEl.addEventListener("pointermove", function (e) {
      if (!dragging) return;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      if (!moved && Math.abs(dx) + Math.abs(dy) < 6) return;
      moved = true;
      const left = Math.min(Math.max(4, originLeft + dx), window.innerWidth - 50);
      const top = Math.min(Math.max(4, originTop + dy), window.innerHeight - 50);
      btnEl.style.left = left + "px";
      btnEl.style.top = top + "px";
      btnEl.style.right = "auto";
      btnEl.style.bottom = "auto";
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
      if (!moved) {
        togglePanel();
        return;
      }
      snapToEdge();
    });

    btnEl.addEventListener("pointercancel", function () {
      dragging = false;
    });

    btnEl.addEventListener("pointerenter", function () {
      btnEl.classList.remove("nv-idle");
      clearTimeout(idleTimer);
    });
    btnEl.addEventListener("pointerleave", function () {
      scheduleIdle();
    });
  }

  function snapToEdge() {
    const rect = btnEl.getBoundingClientRect();
    const margin = 12;
    const toLeft = rect.left + rect.width / 2 < window.innerWidth / 2;
    const top = Math.min(Math.max(margin, rect.top), window.innerHeight - rect.height - margin);
    const left = toLeft ? margin : window.innerWidth - rect.width - margin;
    btnEl.style.left = left + "px";
    btnEl.style.top = top + "px";
    btnEl.style.right = "auto";
    btnEl.style.bottom = "auto";
    savePos(left, top);
    positionPanel();
  }

  function scheduleIdle() {
    clearTimeout(idleTimer);
    idleTimer = setTimeout(function () {
      if (!panelOpen) btnEl.classList.add("nv-idle");
    }, 3200);
  }

  function applySavedPos() {
    browser.storage.local.get(POS_KEY).then(function (res) {
      const pos = res && res[POS_KEY];
      if (pos && typeof pos.left === "number") {
        const left = Math.min(Math.max(4, pos.left), window.innerWidth - 50);
        const top = Math.min(Math.max(4, pos.top), window.innerHeight - 50);
        btnEl.style.left = left + "px";
        btnEl.style.top = top + "px";
        btnEl.style.right = "auto";
        btnEl.style.bottom = "auto";
      } else {
        btnEl.style.right = "14px";
        btnEl.style.bottom = "110px";
      }
      positionPanel();
    });
  }

  function savePos(left, top) {
    browser.storage.local.set({ [POS_KEY]: { left: left, top: top } }).catch(function () {});
  }

  function positionPanel() {
    if (!panelEl || panelEl.hidden) return;
    const margin = 10;
    const rect = btnEl.getBoundingClientRect();
    const panelRect = panelEl.getBoundingClientRect();
    const width = panelRect.width || 330;
    const height = panelRect.height || 300;
    let left = rect.left + rect.width / 2 < window.innerWidth / 2
      ? margin
      : window.innerWidth - width - margin;
    let top = rect.top - height - 10;
    if (top < margin) top = rect.bottom + 10;
    if (top + height > window.innerHeight - margin) {
      top = Math.max(margin, window.innerHeight - height - margin);
    }
    left = Math.min(Math.max(margin, left), window.innerWidth - width - margin);
    panelEl.style.left = left + "px";
    panelEl.style.top = top + "px";
    panelEl.style.right = "auto";
    panelEl.style.bottom = "auto";
  }

  /* -------------------------- prefs ------------------------------- */

  function savePrefs() {
    browser.storage.local.set({ [PREFS_KEY]: prefs }).catch(function () {});
  }

  function loadPrefs() {
    return browser.storage.local
      .get(PREFS_KEY)
      .then(function (res) {
        const saved = res && res[PREFS_KEY];
        if (saved && typeof saved === "object") {
          prefs.floatEnabled = saved.floatEnabled !== false;
          prefs.hiddenHosts = Array.isArray(saved.hiddenHosts) ? saved.hiddenHosts : [];
        }
      })
      .catch(function () {});
  }

  function hiddenHere() {
    return prefs.hiddenHosts.indexOf(location.hostname) > -1;
  }

  /* -------------------------- panel ------------------------------- */

  function togglePanel() {
    if (panelOpen) closePanel();
    else openPanel();
  }

  function openPanel() {
    panelOpen = true;
    panelEl.hidden = false;
    btnEl.classList.remove("nv-idle");
    clearTimeout(idleTimer);
    renderList();
    positionPanel();
    refreshEntries(true);
  }

  function closePanel() {
    panelOpen = false;
    panelEl.hidden = true;
    scheduleIdle();
  }

  function onFullscreenChange() {
    if (!host) return;
    if (document.fullscreenElement) {
      host.style.display = "none";
    } else {
      host.style.display = "";
      positionPanel();
    }
  }

  function checkVisibility() {
    if (!host) return;
    if (!entries.length || !prefs.floatEnabled || hiddenHere()) {
      btnEl.hidden = true;
      if (!entries.length) closePanel();
    } else {
      btnEl.hidden = false;
      applySavedPos();
      scheduleIdle();
    }
  }

  function toast(message) {
    if (!toastEl) return;
    toastEl.textContent = message;
    toastEl.hidden = false;
    toastEl.style.left = "50%";
    toastEl.style.bottom = "24px";
    toastEl.style.transform = "translateX(-50%)";
    clearTimeout(toastEl.__timer);
    toastEl.__timer = setTimeout(function () {
      toastEl.hidden = true;
    }, 3400);
  }

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
    return "Video";
  }

  function renderList() {
    if (!listEl) return;
    rowRefs = new Map();
    listEl.innerHTML = "";
    if (!entries.length) {
      const empty = document.createElement("div");
      empty.className = "nv-empty";
      empty.textContent = "No videos found on this page yet. Press play, then Rescan.";
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
    kind.textContent = kindLabel(entry.kind);
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
    const metaBits = [];
    if (entry.width && entry.height) metaBits.push(entry.width + "\u00d7" + entry.height);
    if (entry.duration) metaBits.push(fmtDuration(entry.duration));
    if (entry.kind === "hls" || entry.kind === "dash") metaBits.push("stream");
    metaBits.push(entry.url.length > 90 ? entry.url.slice(0, 90) + "\u2026" : entry.url);
    meta.textContent = metaBits.join("  \u00b7  ");
    row.appendChild(meta);

    const actions = document.createElement("div");
    actions.className = "nv-actions";

    const go = document.createElement("button");
    go.className = "nv-go";
    go.textContent = "Download";
    go.addEventListener("click", function () {
      startDownload(entry);
    });
    actions.appendChild(go);

    const copy = document.createElement("button");
    copy.className = "nv-copy";
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

    rowRefs.set(entry.url, { row, status, prog, bar, go, select });

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
    if (!ref) return;
    ref.go.disabled = isBusy;
    if (isBusy) {
      if (busy.size === 0) { /* noop */ }
      busy.add(url);
    } else {
      busy.delete(url);
    }
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

  function optionValue(value, label) {
    const opt = document.createElement("option");
    opt.value = value;
    opt.textContent = label;
    return opt;
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
    setStatus(entry.url, hasVideo ? "Video + audio are separate tracks" : "Audio only");
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
    const blob = new Blob([bytes], { type: entry.contentType || (entry.kind === "audio" ? "audio/mpeg" : "video/mp4") });
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
    if (rep.incomplete) {
      throw new Error("Unsupported DASH layout - use Copy link");
    }
    if (rep.single) {
      const bytes = await fetchBytes(rep.segments[0]);
      saveBlob(new Blob([bytes], { type: rep.mime || "video/mp4" }), guessName(entry, rep.type === "audio" ? "m4a" : "mp4"));
      return;
    }
    const ext = rep.type === "audio" ? "m4a" : "mp4";
    const mime = rep.mime || (rep.type === "audio" ? "audio/mp4" : "video/mp4");
    setStatus(entry.url, "Downloading " + rep.segments.length + " segments\u2026");
    await downloadSegments(rep.segments, guessName(entry, ext), mime, entry.url);
  }

  /* -------------------------- entry polling ----------------------- */

  function refreshEntries(force) {
    if (!host) return;
    send("novaVideo:get").then(function (res) {
      const next = (res && res.ok && Array.isArray(res.entries)) ? res.entries : [];
      const changed = next.length !== entries.length ||
        next.some(function (entry, i) {
          return !entries[i] || entries[i].url !== entry.url;
        });
      entries = next;
      const badge = btnEl.querySelector(".nv-badge");
      if (badge) {
        badge.hidden = entries.length < 2;
        badge.textContent = String(entries.length);
      }
      checkVisibility();
      if (panelOpen && (changed || force)) renderList();
    });
  }

  /* -------------------------- boot -------------------------------- */

  function boot() {
    buildUi();
    loadPrefs().then(function () {
      checkVisibility();
      refreshEntries(true);
      setInterval(refreshEntries, 3000);
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot, { once: true });
  } else {
    boot();
  }
})();
