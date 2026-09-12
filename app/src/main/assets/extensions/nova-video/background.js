"use strict";

/*
 * Nova Video Downloader - background script.
 *
 * Observes network traffic to find media the page is loading: progressive
 * files (Content-Type video/audio or a known file extension) and streaming
 * manifests (HLS .m3u8 / DASH .mpd). The content script asks for the list,
 * renders the picker UI and asks us to resolve playlists into a quality
 * ladder / segment list.
 */

const DIRECT_EXT = new Set([
  "mp4", "m4v", "webm", "mkv", "mov", "avi", "flv", "mpg", "mpeg", "ogv",
  "3gp", "3g2", "mp3", "m4a", "mp4a", "m4b", "aac", "wav", "ogg", "oga",
  "opus", "flac", "weba",
]);
const AUDIO_EXT = new Set([
  "mp3", "m4a", "mp4a", "m4b", "aac", "wav", "ogg", "oga", "opus", "flac", "weba",
]);
const HLS_EXT = new Set(["m3u8", "m3u"]);
const DASH_EXT = new Set(["mpd"]);

const MAX_ENTRIES_PER_TAB = 80;

/** Name of the native (Kotlin) bridge that runs the bundled yt-dlp. */
const NATIVE_APP = "novaVideoYtdlp";

/*
 * Sites whose media is not reachable as a plain file/manifest, but which the
 * bundled yt-dlp can still extract. When the page matches, a synthetic entry is
 * offered that hands the page URL to the native bridge.
 */
const YTDLP_SITES = [
  { host: /^(.+\.)?(youtube\.com|youtube-nocookie\.com)$/i, path: /^\/(watch|shorts\/|live\/|embed\/)/i },
  { host: /^youtu\.be$/i, path: /^\/.+/ },
  { host: /^(.+\.)?vimeo\.com$/i, path: /^\/\d+/ },
  { host: /^(.+\.)?dailymotion\.com$/i, path: /^\/video\//i },
  { host: /^(.+\.)?facebook\.com$/i, path: /^\/(watch|reel|videos?)\//i },
  { host: /^fb\.watch$/i, path: /^\/.+/ },
  { host: /^(.+\.)?instagram\.com$/i, path: /^\/(reel|reels|p|tv)\//i },
  { host: /^(.+\.)?(twitter|x)\.com$/i, path: /\/status\//i },
  { host: /^(.+\.)?tiktok\.com$/i, path: /^\/(@|video\/)/i },
];

function isYtdlpPage(url) {
  if (!url) return false;
  try {
    const u = new URL(url);
    const host = u.hostname.toLowerCase();
    for (const site of YTDLP_SITES) {
      if (site.host.test(host) && site.path.test(u.pathname)) return true;
    }
    return false;
  } catch (e) {
    return false;
  }
}

function ytdlpLabel(url) {
  try {
    const host = new URL(url).hostname.replace(/^www\./, "").toLowerCase();
    if (host.endsWith("youtube.com") || host === "youtu.be") return "YouTube";
    if (host.endsWith("vimeo.com")) return "Vimeo";
    if (host.endsWith("dailymotion.com")) return "Dailymotion";
    if (host.endsWith("facebook.com") || host === "fb.watch") return "Facebook";
    if (host.endsWith("instagram.com")) return "Instagram";
    if (host === "x.com" || host.endsWith("twitter.com")) return "X";
    if (host.endsWith("tiktok.com")) return "TikTok";
    return "Site";
  } catch (e) {
    return "Site";
  }
}

/** tabId -> Map(url -> entry) */
const tabMedia = new Map();
/** tabId -> Map(frameUrl -> { title, elements: [...] }) */
const tabElements = new Map();

function getTabMap(tabId) {
  let map = tabMedia.get(tabId);
  if (!map) {
    map = new Map();
    tabMedia.set(tabId, map);
  }
  return map;
}

function getElementMap(tabId) {
  let map = tabElements.get(tabId);
  if (!map) {
    map = new Map();
    tabElements.set(tabId, map);
  }
  return map;
}

function clearTab(tabId) {
  tabMedia.delete(tabId);
  tabElements.delete(tabId);
}

function pathExt(url) {
  let path = url.split("#")[0].split("?")[0];
  const dot = path.lastIndexOf(".");
  if (dot < 0) return "";
  return path.slice(dot + 1).toLowerCase();
}

function kindFromUrl(url) {
  const ext = pathExt(url);
  if (!ext) return null;
  if (HLS_EXT.has(ext)) return "hls";
  if (DASH_EXT.has(ext)) return "dash";
  if (DIRECT_EXT.has(ext)) return AUDIO_EXT.has(ext) ? "audio" : "video";
  return null;
}

function kindFromContentType(ct) {
  if (!ct) return null;
  const lc = ct.toLowerCase();
  if (lc.includes("mpegurl")) return "hls";
  if (lc.includes("dash+xml")) return "dash";
  if (lc.startsWith("video/")) return "video";
  if (lc.startsWith("audio/")) return "audio";
  return null;
}

const KIND_RANK = { ytdlp: 6, hls: 4, dash: 4, video: 3, audio: 2 };

function addEntry(tabId, patch) {
  if (!patch || !patch.url) return;
  const map = getTabMap(tabId);
  const existing = map.get(patch.url);
  if (existing) {
    if (patch.kind && KIND_RANK[patch.kind] > KIND_RANK[existing.kind]) {
      existing.kind = patch.kind;
    }
    if (patch.contentType) existing.contentType = patch.contentType;
    if (patch.contentLength) existing.contentLength = patch.contentLength;
    existing.ts = Date.now();
    return;
  }
  map.set(patch.url, {
    url: patch.url,
    kind: patch.kind,
    contentType: patch.contentType || null,
    contentLength: patch.contentLength || null,
    ts: Date.now(),
  });
  if (map.size > MAX_ENTRIES_PER_TAB) {
    let oldestKey = null;
    let oldest = Infinity;
    for (const [key, value] of map) {
      if (value.ts < oldest) {
        oldest = value.ts;
        oldestKey = key;
      }
    }
    if (oldestKey) map.delete(oldestKey);
  }
}

function headerValue(headers, name) {
  if (!headers) return null;
  const lc = name.toLowerCase();
  for (const h of headers) {
    if (h.name && h.name.toLowerCase() === lc) return h.value || null;
  }
  return null;
}

const SNIFF_TYPES = ["media", "xmlhttprequest", "other", "object", "sub_frame", "main_frame"];

browser.webRequest.onBeforeRequest.addListener(
  function (details) {
    if (details.tabId < 0) return;
    if (details.type === "main_frame") {
      clearTab(details.tabId);
      return;
    }
    const kind = kindFromUrl(details.url);
    if (!kind) return;
    addEntry(details.tabId, { url: details.url, kind });
  },
  { urls: ["<all_urls>"], types: SNIFF_TYPES }
);

browser.webRequest.onHeadersReceived.addListener(
  function (details) {
    if (details.tabId < 0) return;
    const contentType = headerValue(details.responseHeaders, "content-type");
    const rawLength = headerValue(details.responseHeaders, "content-length");
    const contentLength = rawLength ? parseInt(rawLength, 10) : 0;
    let kind = kindFromContentType(contentType);
    if (!kind) kind = kindFromUrl(details.url);
    if (!kind) return;
    addEntry(details.tabId, {
      url: details.url,
      kind,
      contentType: contentType || null,
      contentLength: contentLength > 0 ? contentLength : null,
    });
  },
  { urls: ["<all_urls>"], types: SNIFF_TYPES },
  ["responseHeaders"]
);

browser.tabs.onRemoved.addListener(function (tabId) {
  clearTab(tabId);
});

/* ------------------------------------------------------------------ */
/* URL helpers                                                         */
/* ------------------------------------------------------------------ */

function absUrl(uri, base) {
  try {
    return new URL(uri, base).href;
  } catch (e) {
    return uri;
  }
}

function parseAttrs(str) {
  const out = {};
  const re = /([A-Za-z0-9_.-]+)\s*=\s*("[^"]*"|[^,]*)/g;
  let m;
  while ((m = re.exec(str))) {
    let value = m[2];
    if (value.length >= 2 && value[0] === '"' && value[value.length - 1] === '"') {
      value = value.slice(1, -1);
    }
    out[m[1].toUpperCase()] = value.trim();
  }
  return out;
}

/* ------------------------------------------------------------------ */
/* HLS                                                                 */
/* ------------------------------------------------------------------ */

function hlsVariantName(attrs) {
  if (attrs.RESOLUTION) {
    const parts = attrs.RESOLUTION.toLowerCase().split("x");
    if (parts.length === 2 && parseInt(parts[1], 10) >= 720) return parts[1] + "p HD";
    if (parts.length === 2) return parts[1] + "p";
  }
  if (attrs.NAME) return attrs.NAME;
  if (attrs.BANDWIDTH) return Math.round(parseInt(attrs.BANDWIDTH, 10) / 1000) + " kbps";
  return "Stream";
}

function parseHlsMaster(text, base) {
  const lines = text.split(/\r?\n/);
  const variants = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line.startsWith("#EXT-X-STREAM-INF:")) continue;
    const attrs = parseAttrs(line.slice("#EXT-X-STREAM-INF:".length));
    let uri = "";
    for (let j = i + 1; j < lines.length; j++) {
      const next = lines[j].trim();
      if (!next) continue;
      if (next.startsWith("#")) break;
      uri = next;
      break;
    }
    if (!uri) continue;
    let height = 0;
    if (attrs.RESOLUTION && attrs.RESOLUTION.indexOf("x") > -1) {
      height = parseInt(attrs.RESOLUTION.split("x")[1], 10) || 0;
    }
    variants.push({
      url: absUrl(uri, base),
      bandwidth: parseInt(attrs.BANDWIDTH, 10) || 0,
      resolution: attrs.RESOLUTION || "",
      height,
      name: hlsVariantName(attrs),
    });
  }
  variants.sort(function (a, b) {
    return b.bandwidth - a.bandwidth || b.height - a.height;
  });
  return variants;
}

function parseHlsMedia(text, base) {
  const lines = text.split(/\r?\n/);
  const segments = [];
  let hasInit = false;
  let encrypted = false;
  let duration = 0;
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    if (line.startsWith("#EXTINF:")) {
      duration += parseFloat(line.slice("#EXTINF:".length)) || 0;
      continue;
    }
    if (line.startsWith("#EXT-X-MAP:")) {
      const attrs = parseAttrs(line.slice("#EXT-X-MAP:".length));
      if (attrs.URI) {
        segments.push(absUrl(attrs.URI, base));
        hasInit = true;
      }
      continue;
    }
    if (line.startsWith("#EXT-X-KEY:")) {
      const attrs = parseAttrs(line.slice("#EXT-X-KEY:".length));
      if (attrs.METHOD && attrs.METHOD.toUpperCase() !== "NONE") encrypted = true;
      continue;
    }
    if (line.startsWith("#")) continue;
    segments.push(absUrl(line, base));
  }
  return { segments, hasInit, encrypted, duration };
}

function resolveHls(url) {
  return fetch(url, { credentials: "include" })
    .then(function (res) {
      if (!res.ok) throw new Error("HTTP " + res.status);
      return res.text().then(function (text) {
        const base = res.url || url;
        if (/#EXT-X-STREAM-INF:/.test(text)) {
          const variants = parseHlsMaster(text, base);
          if (variants.length) {
            return { ok: true, type: "master", variants };
          }
        }
        const media = parseHlsMedia(text, base);
        if (!media.segments.length) throw new Error("No segments found in playlist");
        return {
          ok: true,
          type: "media",
          segments: media.segments,
          hasInit: media.hasInit,
          encrypted: media.encrypted,
          duration: media.duration,
        };
      });
    })
    .catch(function (e) {
      return { ok: false, error: String((e && e.message) || e) };
    });
}

/* ------------------------------------------------------------------ */
/* DASH                                                                */
/* ------------------------------------------------------------------ */

function mimeToType(mime) {
  if (!mime) return "";
  const lc = mime.toLowerCase();
  if (lc.startsWith("video/")) return "video";
  if (lc.startsWith("audio/")) return "audio";
  return "";
}

function childByName(el, name) {
  if (!el) return null;
  const kids = el.children || [];
  for (let i = 0; i < kids.length; i++) {
    if (kids[i].localName === name) return kids[i];
  }
  return null;
}

function textOf(el) {
  return el && el.textContent ? el.textContent.trim() : "";
}

function fillTemplate(tpl, values) {
  return tpl.replace(/\$(\w+)\$|(\$\$)/g, function (all, name) {
    if (all === "$$") return "$";
    if (!name) return all;
    const key = name.toUpperCase();
    if (key === "REPRESENTATIONID") return values.representationId || "";
    if (key === "BANDWIDTH") return String(values.bandwidth || "");
    if (key === "NUMBER") return String(values.number == null ? "" : values.number);
    if (key === "TIME") return String(values.time == null ? "" : values.time);
    return all;
  });
}

function dashSegmentsFromTemplate(template, base, values, totalDuration, timescale) {
  const out = [];
  const media = template.getAttribute("media") || template.getAttribute("initialization");
  let initUrl = template.getAttribute("initialization");
  if (initUrl) {
    out.push(absUrl(fillTemplate(initUrl, values), base));
  }
  const mediaTpl = template.getAttribute("media");
  if (!mediaTpl) return { segments: out, hasInit: !!initUrl };

  const timeline = childByName(template, "SegmentTimeline");
  if (timeline) {
    const scale = parseInt(template.getAttribute("timescale") || timescale || "1", 10) || 1;
    let time = 0;
    const ss = timeline.children || [];
    for (let i = 0; i < ss.length; i++) {
      if (ss[i].localName !== "S") continue;
      const t = ss[i].getAttribute("t");
      if (t != null) time = parseInt(t, 10) || 0;
      const d = parseInt(ss[i].getAttribute("d") || "0", 10) || 0;
      const r = parseInt(ss[i].getAttribute("r") || "0", 10) || 0;
      for (let k = 0; k <= r; k++) {
        out.push(absUrl(fillTemplate(mediaTpl, Object.assign({ time }, values)), base));
        time += d;
      }
    }
    return { segments: out, hasInit: !!initUrl };
  }

  const duration = parseInt(template.getAttribute("duration") || "0", 10) || 0;
  const scale = parseInt(template.getAttribute("timescale") || timescale || "1", 10) || 1;
  const startNumber = parseInt(template.getAttribute("startNumber") || "1", 10) || 1;
  if (!duration || !totalDuration) return { segments: out, hasInit: !!initUrl, incomplete: true };
  const segDuration = duration / scale;
  const count = Math.max(1, Math.ceil(totalDuration / segDuration));
  for (let i = 0; i < count && i < 20000; i++) {
    out.push(absUrl(fillTemplate(mediaTpl, Object.assign({ number: startNumber + i }, values)), base));
  }
  return { segments: out, hasInit: !!initUrl };
}

function dashSegmentsFromList(list, base, values) {
  const out = [];
  const init = childByName(list, "Initialization");
  if (init && (init.getAttribute("sourceURL") || init.getAttribute("media"))) {
    out.push(absUrl(fillTemplate(init.getAttribute("sourceURL") || init.getAttribute("media"), values), base));
  }
  const urls = list.children || [];
  for (let i = 0; i < urls.length; i++) {
    if (urls[i].localName !== "SegmentURL") continue;
    const media = urls[i].getAttribute("media");
    if (media) out.push(absUrl(fillTemplate(media, values), base));
  }
  return { segments: out, hasInit: out.length > 0 };
}

function parseDashMpd(text, url) {
  const doc = new DOMParser().parseFromString(text, "application/xml");
  if (doc.getElementsByTagName("parsererror").length) {
    return { ok: false, error: "Invalid MPD document" };
  }
  const mpd = doc.documentElement;
  if (!mpd || mpd.localName !== "MPD") return { ok: false, error: "Not an MPD document" };

  const totalDuration = parseDuration(mpd.getAttribute("mediaPresentationDuration"));
  const reps = [];

  const mpdBaseEl = childByName(mpd, "BaseURL");
  let rootBase = url;
  if (mpdBaseEl) rootBase = absUrl(textOf(mpdBaseEl), url);

  const periods = doc.getElementsByTagName("Period");
  const period = periods.length ? periods[0] : null;
  const periodBaseEl = period ? childByName(period, "BaseURL") : null;
  const periodBase = periodBaseEl ? absUrl(textOf(periodBaseEl), rootBase) : rootBase;

  const adaptSets = doc.getElementsByTagName("AdaptationSet");
  for (let a = 0; a < adaptSets.length; a++) {
    const as = adaptSets[a];
    const asMime = as.getAttribute("mimeType") || "";
    const asType = as.getAttribute("contentType") || mimeToType(asMime);
    const asBaseEl = childByName(as, "BaseURL");
    const asBase = asBaseEl ? absUrl(textOf(asBaseEl), periodBase) : periodBase;
    const asTemplate = childByName(as, "SegmentTemplate");
    const asList = childByName(as, "SegmentList");

    const list = [];
    for (let i = 0; i < as.children.length; i++) {
      if (as.children[i].localName === "Representation") list.push(as.children[i]);
    }
    if (!list.length) {
      const all = as.getElementsByTagName("Representation");
      for (let i = 0; i < all.length; i++) list.push(all[i]);
    }

    for (const rep of list) {
      const mime = rep.getAttribute("mimeType") || asMime;
      const type = rep.getAttribute("contentType") || asType || mimeToType(mime);
      const repBaseEl = childByName(rep, "BaseURL");
      const repBase = repBaseEl ? absUrl(textOf(repBaseEl), asBase) : asBase;
      const values = {
        representationId: rep.getAttribute("id") || "",
        bandwidth: parseInt(rep.getAttribute("bandwidth") || as.getAttribute("bandwidth") || "0", 10) || 0,
      };
      const template = childByName(rep, "SegmentTemplate") || asTemplate;
      const segList = childByName(rep, "SegmentList") || asList;
      const timescale = (template && template.getAttribute("timescale")) || (asTemplate && asTemplate.getAttribute("timescale")) || "1";

      let result = null;
      if (template) {
        result = dashSegmentsFromTemplate(template, repBase, values, totalDuration, timescale);
      } else if (segList) {
        result = dashSegmentsFromList(segList, repBase, values);
      }
      if (result && result.segments && result.segments.length) {
        reps.push({
          id: values.representationId || "",
          type: type || "video",
          mime: mime || "",
          codecs: rep.getAttribute("codecs") || as.getAttribute("codecs") || "",
          bandwidth: values.bandwidth,
          width: parseInt(rep.getAttribute("width") || as.getAttribute("width") || "0", 10) || 0,
          height: parseInt(rep.getAttribute("height") || as.getAttribute("height") || "0", 10) || 0,
          segments: result.segments,
          hasInit: !!result.hasInit,
          incomplete: !!result.incomplete,
          single: false,
        });
      } else {
        reps.push({
          id: values.representationId || "",
          type: type || "video",
          mime: mime || "",
          codecs: rep.getAttribute("codecs") || as.getAttribute("codecs") || "",
          bandwidth: values.bandwidth,
          width: parseInt(rep.getAttribute("width") || as.getAttribute("width") || "0", 10) || 0,
          height: parseInt(rep.getAttribute("height") || as.getAttribute("height") || "0", 10) || 0,
          segments: [repBase],
          hasInit: false,
          incomplete: false,
          single: true,
        });
      }
    }
  }

  if (!reps.length) return { ok: false, error: "No downloadable representations found" };
  reps.sort(function (a, b) {
    const rank = { video: 2, audio: 1 };
    const t = (rank[b.type] || 0) - (rank[a.type] || 0);
    return t || b.bandwidth - a.bandwidth;
  });
  return { ok: true, reps };
}

function parseDuration(value) {
  if (!value) return 0;
  const m = /^P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?$/.exec(value);
  if (!m) return 0;
  return (parseInt(m[1] || "0", 10) * 86400) + (parseInt(m[2] || "0", 10) * 3600) + (parseInt(m[3] || "0", 10) * 60) + (parseFloat(m[4] || "0"));
}

/* ------------------------------------------------------------------ */
/* Binary fetch (fallback when a content-script fetch is blocked)      */
/* ------------------------------------------------------------------ */

function bytesToBase64(bytes) {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

function fetchBinary(url) {
  return fetch(url, { credentials: "include" })
    .then(function (res) {
      if (!res.ok) throw new Error("HTTP " + res.status);
      return res.arrayBuffer().then(function (buf) {
        return { ok: true, base64: bytesToBase64(new Uint8Array(buf)) };
      });
    })
    .catch(function (e) {
      return { ok: false, error: String((e && e.message) || e) };
    });
}

/* ------------------------------------------------------------------ */
/* Messaging                                                           */
/* ------------------------------------------------------------------ */

function tabIdOf(sender) {
  return sender && sender.tab && typeof sender.tab.id === "number" ? sender.tab.id : -1;
}

function collectEntries(tabId, pageUrl, pageTitle) {
  const out = [];
  if (isYtdlpPage(pageUrl)) {
    out.push({
      url: pageUrl,
      kind: "ytdlp",
      ytdlp: true,
      site: ytdlpLabel(pageUrl),
      title: pageTitle || ytdlpLabel(pageUrl),
      contentLength: 0,
    });
  }
  const map = tabMedia.get(tabId);
  if (map) {
    for (const entry of map.values()) out.push(entry);
  }
  const elements = tabElements.get(tabId);
  if (elements) {
    for (const report of elements.values()) {
      for (const el of report.elements) {
        if (out.some(function (e) { return e.url === el.url; })) continue;
        const kind = el.tag === "audio" || kindFromUrl(el.url) === "audio" ? "audio" : "video";
        out.push({
          url: el.url,
          kind: kindFromUrl(el.url) || kind,
          fromElement: true,
          width: el.width || 0,
          height: el.height || 0,
          duration: el.duration || 0,
          pageUrl: report.pageUrl,
          title: report.title,
        });
      }
    }
  }
  out.sort(function (a, b) {
    const t = (KIND_RANK[b.kind] || 0) - (KIND_RANK[a.kind] || 0);
    if (t) return t;
    return (b.contentLength || 0) - (a.contentLength || 0);
  });
  return out;
}

function callNativeYtdlp(message) {
  const payload = {
    action: message.action || "start",
    url: message.url || "",
    audioOnly: !!message.audioOnly,
  };
  if (message.id) payload.id = message.id;
  if (!browser.runtime || typeof browser.runtime.sendNativeMessage !== "function") {
    return Promise.resolve({ ok: false, error: "This build has no yt-dlp bridge." });
  }
  return browser.runtime
    .sendNativeMessage(NATIVE_APP, payload)
    .then(function (res) {
      return res || { ok: false, error: "no response" };
    })
    .catch(function (e) {
      return { ok: false, error: String((e && e.message) || e) };
    });
}

browser.runtime.onMessage.addListener(function (message, sender) {
  if (!message || typeof message.type !== "string") return;
  const tabId = tabIdOf(sender);

  switch (message.type) {
    case "novaVideo:get": {
      return Promise.resolve({
        ok: true,
        entries: collectEntries(tabId, message.pageUrl, message.pageTitle),
      });
    }
    case "novaVideo:ytdlp": {
      return callNativeYtdlp(message);
    }
    case "novaVideo:resolveHls": {
      return resolveHls(message.url);
    }
    case "novaVideo:resolveDash": {
      return fetch(message.url, { credentials: "include" })
        .then(function (res) {
          if (!res.ok) throw new Error("HTTP " + res.status);
          return res.text().then(function (text) {
            return parseDashMpd(text, res.url || message.url);
          });
        })
        .catch(function (e) {
          return { ok: false, error: String((e && e.message) || e) };
        });
    }
    case "novaVideo:fetchBinary": {
      return fetchBinary(message.url);
    }
    case "novaVideo:reportElements": {
      if (tabId >= 0) {
        const map = getElementMap(tabId);
        const key = (sender.url || "") + "|" + (message.title || "");
        map.set(key, {
          pageUrl: sender.url || "",
          title: message.title || "",
          elements: Array.isArray(message.elements) ? message.elements.slice(0, 40) : [],
          ts: Date.now(),
        });
      }
      return Promise.resolve({ ok: true });
    }
    default:
      return Promise.resolve({ ok: false, error: "unknown message" });
  }
});
