#!/usr/bin/env bash
# Nova Browser branding overlay — cosmetic only. The engine, GeckoView and every
# feature are byte-identical to upstream IceRaven (iceraven-2.46.0). This script
# re-labels the visible product, removes the "produced by @fork-maintainers"
# credit line from the About page, and bundles two ad-blocking WebExtensions
# (uBlock Origin + the Nova Ad Block host blocker) as built-in add-ons.
# Runs in the iceraven repo root.
set -euo pipefail

NOVA_PRIMARY="#0B7E78"
NOVA_SLATE="#17343C"

echo ">> Nova branding: product name"
# Upstream's CI already replaced "Firefox" -> "Iceraven"; we turn that into "Nova"
find app/src -path "*/res/*/*.xml" -type f -exec sed -i 's/Iceraven/Nova/g' {} +

# Launcher label: "Nova Browser"
sed -i 's#<string name="app_name" translatable="false">Nova</string>#<string name="app_name" translatable="false">Nova Browser</string>#' app/src/forkRelease/res/values/static_strings.xml

# About-page credit line: drop the "produced by ..." text entirely (any language),
# leaving just the app name. Upstream CI already rewrote "Mozilla" to "@forkmaintainers"
# in every locale, so we strip whatever sits between the string tags.
sed -i -E '/name="about_content"/s#>[^<]*</string>#>%1\$s</string>#' app/src/*/res/values*/*strings.xml

# "What's new" must point at our repo, not the upstream one
sed -i 's#https://github.com/fork-maintainers/iceraven-browser/releases#https://github.com/codegeasse1/nova-browser/releases#' \
  app/src/main/java/org/mozilla/fenix/settings/SupportUtils.kt

echo ">> Nova branding: app identity"
sed -i 's/applicationId "io.github.forkmaintainers"/applicationId "com.nova.browser"/' app/build.gradle
sed -i 's/io.github.forkmaintainers.iceraven.sharedID/com.nova.browser.sharedID/g' app/build.gradle
sed -i 's/deepLinkSchemeValue = "iceraven-debug"/deepLinkSchemeValue = "nova-debug"/' app/build.gradle
sed -i 's/deepLinkSchemeValue = "iceraven"/deepLinkSchemeValue = "nova"/' app/build.gradle
sed -i 's/applicationIdSuffix "\.iceraven"/applicationIdSuffix ""/' app/build.gradle

echo ">> Nova branding: teal accent palette"
cat > app/src/forkRelease/res/values/colors.xml <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#0B7E78</color>
    <color name="novaViolet0">#EAF6F5</color>
    <color name="novaViolet5">#D6EFEC</color>
    <color name="novaViolet10">#BFE5E1</color>
    <color name="novaViolet15">#A8DCD7</color>
    <color name="novaViolet20">#8FCFC9</color>
    <color name="novaViolet25">#76C2BB</color>
    <color name="novaViolet30">#5FB7B0</color>
    <color name="novaViolet40">#2E9E95</color>
    <color name="novaViolet50">#178D84</color>
    <color name="novaViolet60">#0B7E78</color>
    <color name="novaViolet70">#0B7E78</color>
    <color name="novaViolet80">#08564F</color>
    <color name="novaViolet90">#063B37</color>
    <color name="novaViolet100">#042622</color>
    <color name="novaVioletDesaturated10">#DCE7E6</color>
    <color name="novaVioletDesaturated30">#AFC4C1</color>
    <color name="novaVioletDesaturated50">#84A09D</color>
    <color name="novaVioletDesaturated70">#5C7572</color>
    <color name="novaVioletDesaturated90">#2E3E3C</color>
    <color name="novaVioletDesaturated90A70">#4D2E3E3C</color>
</resources>
XML

echo ">> Nova branding: launcher icons (teal + white N)"
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  mip=app/src/forkRelease/res/mipmap-$d
  cp nova-icons/mipmap-$d/ic_launcher.png $mip/ic_launcher.png
  cp nova-icons/mipmap-$d/ic_launcher.png $mip/ic_launcher_round.png
  cp nova-icons/mipmap-$d/ic_launcher_private.png $mip/ic_launcher_private.png
  cp nova-icons/mipmap-$d/ic_launcher_private.png $mip/ic_launcher_private_round.png
done
cp nova-icons/drawable-hdpi/fenix_search_widget.png app/src/forkRelease/res/drawable-hdpi/fenix_search_widget.png

echo ">> Nova branding: wordmarks"
cp nova-icons/drawable/ic_wordmark_logo.png app/src/forkRelease/res/drawable/ic_wordmark_logo.png
cp nova-icons/drawable/ic_wordmark_text_normal.png app/src/forkRelease/res/drawable/ic_wordmark_text_normal.png
cp nova-icons/drawable/ic_wordmark_text_private.png app/src/forkRelease/res/drawable/ic_wordmark_text_private.png
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  dw=app/src/forkRelease/res/drawable-$d
  cp nova-icons/drawable-$d/ic_logo_wordmark_normal.png $dw/ic_logo_wordmark_normal.png
  cp nova-icons/drawable-$d/ic_logo_wordmark_private.png $dw/ic_logo_wordmark_private.png
done

echo ">> Nova branding: vector layers"
cat > app/src/forkRelease/res/drawable/ic_launcher_foreground.xml <<'XML'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <path
      android:pathData="M33,75 L33,36 L75,72 L75,36"
      android:strokeColor="#FFFFFF"
      android:strokeWidth="16"
      android:strokeLineCap="round"
      android:strokeLineJoin="round"/>
</vector>
XML
cp app/src/forkRelease/res/drawable/ic_launcher_foreground.xml app/src/forkRelease/res/drawable-v24/ic_launcher_foreground.xml

cat > app/src/forkRelease/res/drawable/ic_launcher_monochrome.xml <<'XML'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <path
      android:pathData="M33,75 L33,36 L75,72 L75,36"
      android:strokeColor="#FF000000"
      android:strokeWidth="16"
      android:strokeLineCap="round"
      android:strokeLineJoin="round"/>
</vector>
XML

cat > app/src/main/res/drawable/ic_launcher_private_background.xml <<'XML'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <path
      android:pathData="M0,0 L108,0 L108,108 L0,108 Z"
      android:fillColor="#17343C"/>
</vector>
XML
cat > app/src/main/res/drawable/ic_launcher_private_foreground.xml <<'XML'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <path
      android:pathData="M33,75 L33,36 L75,72 L75,36"
      android:strokeColor="#FFFFFF"
      android:strokeWidth="16"
      android:strokeLineCap="round"
      android:strokeLineJoin="round"/>
</vector>
XML

echo ">> Nova branding: splash screen"
cat > app/src/forkRelease/res/drawable/animated_splash_screen.xml <<'XML'
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt">
    <aapt:attr name="android:drawable">
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="432dp"
            android:height="432dp"
            android:viewportWidth="108"
            android:viewportHeight="108">
            <path
                android:pathData="M0,0 L108,0 L108,108 L0,108 Z"
                android:fillColor="#0B7E78"/>
            <group
                android:name="nova_n">
                <path
                    android:pathData="M38,80 L38,40 L74,72 L74,40"
                    android:strokeColor="#FFFFFF"
                    android:strokeWidth="10"
                    android:strokeLineCap="round"
                    android:strokeLineJoin="round"/>
            </group>
        </vector>
    </aapt:attr>
    <target android:name="nova_n">
        <aapt:attr name="android:animation">
            <set android:interpolator="@android:interpolator/decelerate_cubic"
                android:ordering="together"
                android:repeatMode="reverse"
                android:repeatCount="infinite">
                <objectAnimator
                    android:propertyName="scaleX"
                    android:duration="500"
                    android:valueFrom="0.75"
                    android:valueTo="1"/>
                <objectAnimator
                    android:propertyName="scaleY"
                    android:duration="500"
                    android:valueFrom="0.75"
                    android:valueTo="1"/>
                <objectAnimator
                    android:propertyName="alpha"
                    android:duration="500"
                    android:valueFrom="0.35"
                    android:valueTo="1"/>
            </set>
        </aapt:attr>
    </target>
</animated-vector>
XML

echo ">> Nova adblock: bundle uBlock Origin (built-in add-on)"
rm -rf app/src/main/assets/extensions/ublock_origin
mkdir -p app/src/main/assets/extensions/ublock_origin
unzip -q -o nova-assets/ublock_origin.xpi -d app/src/main/assets/extensions/ublock_origin
# sanity check: manifest must have unpacked
test -f app/src/main/assets/extensions/ublock_origin/manifest.json

echo ">> Nova adblock: bundle Nova Ad Block host blocker (built-in add-on)"
rm -rf app/src/main/assets/extensions/nova-shield
mkdir -p app/src/main/assets/extensions/nova-shield/icons
cp nova-icons/adblock/16.png app/src/main/assets/extensions/nova-shield/icons/16.png
cp nova-icons/adblock/48.png app/src/main/assets/extensions/nova-shield/icons/48.png
cp nova-icons/adblock/96.png app/src/main/assets/extensions/nova-shield/icons/96.png
cp nova-icons/adblock/128.png app/src/main/assets/extensions/nova-shield/icons/128.png

cat > app/src/main/assets/extensions/nova-shield/manifest.json <<'JSON'
{
  "manifest_version": 2,
  "name": "Nova Ad Block",
  "version": "1.1.0",
  "description": "Blocks requests to any domain you add to the block list - individually or via whole hosts files / blocklists (StevenBlack, AdAway, EasyList, ...). Domains on the allow list are never blocked.",
  "icons": {
    "48": "icons/48.png",
    "96": "icons/96.png",
    "128": "icons/128.png"
  },
  "permissions": [
    "webRequest",
    "webRequestBlocking",
    "storage",
    "unlimitedStorage",
    "<all_urls>"
  ],
  "background": {
    "scripts": ["background.js"]
  },
  "browser_action": {
    "default_title": "Nova Ad Block",
    "default_popup": "options.html",
    "default_icon": {
      "16": "icons/16.png",
      "48": "icons/48.png"
    }
  },
  "options_ui": {
    "page": "options.html",
    "open_in_tab": true
  },
  "applications": {
    "gecko": {
      "id": "nova-shield@nova.browser",
      "strict_min_version": "115.0"
    }
  }
}
JSON

cat > app/src/main/assets/extensions/nova-shield/background.js <<'JS'
"use strict";

// Nova Ad Block - host-based blocking.
// Requests to any host matching the block list are cancelled. The allow list
// (whitelist) always wins. Lists live in browser.storage.local and are edited on
// the options page (also reachable from Settings -> Nova Ad Block).

const BLOCK_KEY = "novaBlockedHosts";
const ALLOW_KEY = "novaAllowedHosts";
const HOSTS_KEY = "novaHostsList";

let blockedSet = new Set();
let allowedSet = new Set();
let hostsSet = new Set();

function normalizeEntry(raw) {
  let entry = String(raw || "").trim().toLowerCase();
  if (!entry) return "";
  entry = entry.replace(/^\*\./, ""); // leading "*.example.com"
  entry = entry.replace(/^\./, ""); // leading ".example.com"
  if (entry.startsWith("http://")) entry = entry.slice(7);
  if (entry.startsWith("https://")) entry = entry.slice(8);
  const slash = entry.indexOf("/");
  if (slash > -1) entry = entry.slice(0, slash);
  const at = entry.indexOf("@");
  if (at > -1) entry = entry.slice(at + 1);
  if (entry.startsWith("www.")) entry = entry.slice(4);
  return entry;
}

// Fast matching: walks the host's dot-labels up (e.g. "a.b.example.com" ->
// "b.example.com" -> "example.com") and checks each against the set. One
// request costs only O(host labels), so even huge lists (StevenBlack / AdAway
// have tens of thousands of entries) don't slow browsing down.
function matches(host, set) {
  if (!host) return false;
  if (set.has(host)) return true;
  let dot = host.indexOf(".");
  while (dot > -1) {
    host = host.slice(dot + 1);
    if (set.has(host)) return true;
    dot = host.indexOf(".");
  }
  return false;
}

function hostOf(url) {
  try {
    const host = new URL(url).hostname.toLowerCase();
    return host.startsWith("www.") ? host.slice(4) : host;
  } catch (e) {
    return "";
  }
}

browser.webRequest.onBeforeRequest.addListener(
  function (details) {
    // Never break page navigation or frames - only stop resource requests.
    if (details.type === "main_frame" || details.type === "sub_frame") return {};
    const host = hostOf(details.url);
    if (!host) return {};
    if (matches(host, allowedSet)) return {};
    if (matches(host, blockedSet)) return { cancel: true };
    if (matches(host, hostsSet)) return { cancel: true };
    return {};
  },
  { urls: ["<all_urls>"] },
  ["blocking"]
);

function buildSets(blocked, allowed, hosts) {
  blockedSet = new Set();
  for (const raw of blocked) {
    const e = normalizeEntry(raw);
    if (e) blockedSet.add(e);
  }
  allowedSet = new Set();
  for (const raw of allowed) {
    const e = normalizeEntry(raw);
    if (e) allowedSet.add(e);
  }
  hostsSet = new Set();
  for (const raw of hosts) {
    const e = normalizeEntry(raw);
    if (e) hostsSet.add(e);
  }
}

async function loadLists() {
  try {
    const store = await browser.storage.local.get([BLOCK_KEY, ALLOW_KEY, HOSTS_KEY]);
    buildSets(store[BLOCK_KEY] || [], store[ALLOW_KEY] || [], store[HOSTS_KEY] || []);
  } catch (e) {
    // storage not available yet - retry shortly after startup
    setTimeout(loadLists, 2000);
  }
}

browser.storage.onChanged.addListener((changes, area) => {
  if (area !== "local") return;
  if (changes[BLOCK_KEY] || changes[ALLOW_KEY] || changes[HOSTS_KEY]) {
    browser.storage.local
      .get([BLOCK_KEY, ALLOW_KEY, HOSTS_KEY])
      .then((store) => {
        buildSets(store[BLOCK_KEY] || [], store[ALLOW_KEY] || [], store[HOSTS_KEY] || []);
      })
      .catch(() => {});
  }
});

loadLists();
JS

cat > app/src/main/assets/extensions/nova-shield/options.html <<'HTML'
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Nova Ad Block</title>
<link rel="stylesheet" href="options.css">
</head>
<body>
<header>
  <h1>Nova Ad Block</h1>
  <p class="sub">Requests to domains on the block list are stopped everywhere (all their subdomains too). The allow list always wins. You can also paste a whole <b>hosts file</b> (StevenBlack hosts, AdAway, ...) &mdash; or grab a big blocklist with one tap.</p>
</header>
<section>
  <label for="blocked">Block these domains (one per line)</label>
  <textarea id="blocked" spellcheck="false" placeholder="ads.example.com&#10;tracker.example.net"></textarea>
</section>
<section>
  <label for="allowed">Always allow these domains &mdash; whitelist (one per line)</label>
  <textarea id="allowed" spellcheck="false" placeholder="example.com&#10;payments.example.net"></textarea>
</section>
<section>
  <label for="hosts">Hosts-file blocklist &mdash; paste any hosts file here</label>
  <textarea id="hosts" spellcheck="false" placeholder="0.0.0.0 ads.example.com&#10;127.0.0.1 tracker.example.net&#10;# lines starting with # are ignored"></textarea>
  <div class="presets">
    <button id="preset-stevenblack" type="button">StevenBlack hosts</button>
    <button id="preset-adaway" type="button">AdAway hosts</button>
    <button id="preset-easylist" type="button">EasyList</button>
  </div>
  <div class="urldiv">
    <input id="hostsUrl" type="url" placeholder="or paste a hosts-file URL and tap Import" spellcheck="false">
    <button id="fetchUrl" type="button">Import</button>
  </div>
</section>
<footer>
  <button id="save">Save</button>
  <span id="status"></span>
</footer>
<script src="options.js"></script>
</body>
</html>
HTML

cat > app/src/main/assets/extensions/nova-shield/options.js <<'JS'
"use strict";

const BLOCK_KEY = "novaBlockedHosts";
const ALLOW_KEY = "novaAllowedHosts";
const HOSTS_KEY = "novaHostsList";

const blockedEl = document.getElementById("blocked");
const allowedEl = document.getElementById("allowed");
const hostsEl = document.getElementById("hosts");
const urlInput = document.getElementById("hostsUrl");
const statusEl = document.getElementById("status");
const saveBtn = document.getElementById("save");

const PRESETS = {
  stevenblack: {
    url: "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
    label: "StevenBlack hosts",
  },
  adaway: {
    url: "https://adaway.org/hosts.txt",
    label: "AdAway hosts",
  },
  easylist: {
    url: "https://raw.githubusercontent.com/easylist/easylist/master/easylist.txt",
    label: "EasyList",
  },
};

function setStatus(msg, persistMs) {
  statusEl.textContent = msg;
  clearTimeout(setStatus._t);
  setStatus._t = setTimeout(() => {
    statusEl.textContent = "";
  }, persistMs || 4000);
}

function parseDomains(text) {
  const seen = new Set();
  const out = [];
  for (const line of String(text || "").split(/\r?\n/)) {
    for (let part of line.split(",")) {
      part = part.trim().toLowerCase();
      if (!part) continue;
      part = part.replace(/^\*\./, "").replace(/^\./, "");
      if (part.startsWith("http://")) part = part.slice(7);
      if (part.startsWith("https://")) part = part.slice(8);
      const slash = part.indexOf("/");
      if (slash > -1) part = part.slice(0, slash);
      if (!part || seen.has(part)) continue;
      seen.add(part);
      out.push(part);
    }
  }
  return out;
}

const IP_RE = /^\d{1,3}(\.\d{1,3}){3}$/;
const HOSTNAME_RE = /^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$/;
const SKIP_HOSTS = new Set([
  "localhost", "localhost.localdomain", "broadcasthost",
  "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
  "ip6-allnodes", "ip6-allrouters", "ip6-allhosts",
]);

function parseHostsFile(text) {
  const seen = new Set();
  const out = [];
  for (let line of String(text || "").split(/\r?\n/)) {
    if (line.includes("##") || line.includes("#@#")) continue; // cosmetic filter rules
    line = line.replace(/[#!].*$/, "").trim(); // strip comments (# and !)
    if (!line) continue;
    const tokens = line.split(/\s+/);
    const firstIsIP = IP_RE.test(tokens[0]) || tokens[0].indexOf(":") > -1;
    if (!firstIsIP && tokens.length > 1) continue; // not hosts/adblock format
    for (let t of tokens) {
      t = t.trim().toLowerCase();
      if (!t) continue;
      if (IP_RE.test(t) || t.indexOf(":") > -1) continue; // IP token (IPv4 / IPv6)
      if (t.indexOf("@@") === 0) continue; // adblock exception rules
      if (t.startsWith("||")) t = t.slice(2); // adblock "||domain^" rules
      const caret = t.indexOf("^");
      if (caret > -1) t = t.slice(0, caret);
      if (t.startsWith("*.")) t = t.slice(2);
      const slash = t.indexOf("/");
      if (slash > -1) t = t.slice(0, slash);
      if (!t || seen.has(t)) continue;
      if (SKIP_HOSTS.has(t)) continue;
      if (t.endsWith(".local") || t.endsWith(".lan") || t.endsWith(".localdomain")) continue;
      if (!HOSTNAME_RE.test(t)) continue;
      seen.add(t);
      out.push(t);
    }
  }
  return out;
}

async function save(extraMsg) {
  const blocked = parseDomains(blockedEl.value);
  const allowed = parseDomains(allowedEl.value);
  const hosts = parseHostsFile(hostsEl.value);
  await browser.storage.local.set({ [BLOCK_KEY]: blocked, [ALLOW_KEY]: allowed, [HOSTS_KEY]: hosts });
  const msg = "Saved \u2014 " + blocked.length + " blocked, " + allowed.length + " allowed, " + hosts.length + " hosts blocked";
  setStatus(extraMsg ? extraMsg + " " + msg : msg);
  return { blocked, allowed, hosts };
}

async function fetchAndApply(url, label, btn) {
  const orig = btn ? btn.textContent : null;
  if (btn) { btn.disabled = true; btn.textContent = "Fetching\u2026"; }
  setStatus("Fetching " + label + "\u2026", 60000);
  try {
    const res = await fetch(url, { credentials: "omit" });
    if (!res.ok) throw new Error("HTTP " + res.status);
    const text = await res.text();
    const added = parseHostsFile(text);
    if (!added.length) {
      setStatus("No hosts found in " + label);
      return;
    }
    const merged = new Set(parseHostsFile(hostsEl.value));
    for (const h of added) merged.add(h);
    hostsEl.value = [...merged].join("\n");
    await save(label + ": " + added.length + " hosts imported");
  } catch (e) {
    setStatus("Could not fetch " + label + " \u2014 " + e.message, 8000);
  } finally {
    if (btn) { btn.disabled = false; btn.textContent = orig; }
  }
}

saveBtn.addEventListener("click", () => {
  save().catch((e) => setStatus("Save failed \u2014 " + e.message, 8000));
});

document.getElementById("fetchUrl").addEventListener("click", (ev) => {
  const u = urlInput.value.trim();
  if (!u) {
    setStatus("Paste a hosts-file URL first", 3000);
    return;
  }
  fetchAndApply(u, "URL import", ev.currentTarget).catch(() => {});
});

for (const name of Object.keys(PRESETS)) {
  const btn = document.getElementById("preset-" + name);
  btn.addEventListener("click", (ev) => {
    fetchAndApply(PRESETS[name].url, PRESETS[name].label, ev.currentTarget).catch(() => {});
  });
}

async function init() {
  const store = await browser.storage.local.get([BLOCK_KEY, ALLOW_KEY, HOSTS_KEY]);
  blockedEl.value = (store[BLOCK_KEY] || []).join("\n");
  allowedEl.value = (store[ALLOW_KEY] || []).join("\n");
  hostsEl.value = (store[HOSTS_KEY] || []).join("\n");
}

init().catch(() => {});
JS

cat > app/src/main/assets/extensions/nova-shield/options.css <<'CSS'
* { box-sizing: border-box; }
body {
  margin: 0;
  padding: 20px;
  font-family: -apple-system, "Segoe UI", Roboto, Arial, sans-serif;
  background: #0d2327;
  color: #e6f2f1;
  max-width: 640px;
  margin: 0 auto;
}
header { margin-bottom: 18px; }
h1 { margin: 0 0 6px; font-size: 22px; color: #7fd4cc; }
.sub { margin: 0; font-size: 14px; color: #a9c6c3; line-height: 1.45; }
section { margin-bottom: 16px; }
label { display: block; font-size: 14px; margin-bottom: 6px; color: #cde9e6; }
textarea {
  width: 100%;
  min-height: 110px;
  padding: 10px;
  border: 1px solid #2e5a55;
  border-radius: 8px;
  background: #102e33;
  color: #e6f2f1;
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 14px;
  resize: vertical;
}
textarea:focus { outline: none; border-color: #0B7E78; }
footer { display: flex; align-items: center; gap: 14px; }
button {
  background: #0B7E78;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 10px 26px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}
button:active { background: #0a6b66; }
button:disabled { opacity: 0.6; cursor: default; }
#status { font-size: 13px; color: #7fd4cc; }
.presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.presets button {
  background: #15595a;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 500;
}
.presets button:active { background: #104a4b; }
.urldiv {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
#hostsUrl {
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #2e5a55;
  border-radius: 8px;
  background: #102e33;
  color: #e6f2f1;
  font-size: 13px;
}
#hostsUrl:focus { outline: none; border-color: #0B7E78; }
#hostsUrl::placeholder { color: #5f8b87; }
.urldiv button { padding: 8px 18px; font-size: 13px; font-weight: 500; }
CSS

echo ">> Nova branding: close tabs on exit + bundled adblock wiring"
python3 <<'PY'
import io

def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()

def write(path, content):
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(content)

def patch(path, old, new):
    s = read(path)
    if old not in s:
        raise SystemExit("PATCH FAILED in %s:\npattern not found:\n%s" % (path, old[:200]))
    write(path, s.replace(old, new, 1))

BASE = "app/src/main/java/org/mozilla/fenix/"

# --- New file: close-tabs-on-exit cleanup ------------------------------------
write(BASE + "components/NovaCloseCleanup.kt", r'''/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mozilla.fenix.settings.deletebrowsingdata.DefaultDeleteBrowsingDataController
import org.mozilla.fenix.settings.deletebrowsingdata.DefaultDeleteBrowsingDataController.DataStorage
import org.mozilla.fenix.settings.deletebrowsingdata.DefaultDeleteBrowsingDataController.DeleteDataUseCases
import org.mozilla.fenix.settings.deletebrowsingdata.DefaultDeleteBrowsingDataController.Stores

/**
 * Applies the "app was closed" cleanup, using exactly the user's existing settings:
 *
 *  - "Close tabs when the app is closed": removes every tab and deletes the
 *    persisted session snapshot so the tabs cannot come back on the next launch.
 *  - "Delete browsing data on quit" (IceRaven's own setting): runs the same
 *    delete-on-quit controller the Quit menu item uses.
 *
 * A deliberately small shared helper so the exact same cleanup runs whether the
 * close was detected while the process was alive (the delayed task check) or at
 * the next launch (task id mismatch).
 */
object NovaCloseCleanup {
    fun run(context: Context, components: Components) {
        val settings = components.settings
        var clearedTabs = false
        var clearedData = false

        if (settings.closeTabsOnExit) {
            clearedTabs = true
            try {
                NovaDebugLog.log(context, "NovaCloseCleanup: closing all tabs")
                components.useCases.tabsUseCases.removeAllTabs.invoke(false)
                // The session snapshot on disk would otherwise restore the tabs on the
                // next launch (especially when the process is killed on swipe before the
                // empty state is saved), so delete it explicitly.
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        components.core.sessionStorage.clear()
                        NovaDebugLog.log(context, "NovaCloseCleanup: session snapshot deleted")
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (settings.shouldDeleteBrowsingDataOnQuit) {
            clearedData = true
            try {
                // The same controller (and settings) used by the Quit menu item, so the
                // user's "Delete browsing data on quit" choices are honoured here too.
                val controller = DefaultDeleteBrowsingDataController(
                    deleteDataUseCases = DeleteDataUseCases(
                        removeAllTabs = components.useCases.tabsUseCases.removeAllTabs,
                        removeAllDownloads = components.useCases.downloadUseCases.removeAllDownloads,
                    ),
                    dataStorage = DataStorage(
                        history = components.core.historyStorage,
                        permissions = components.core.permissionStorage,
                    ),
                    stores = Stores(
                        appStore = components.appStore,
                        browserStore = components.core.store,
                    ),
                    engine = components.core.engine,
                    settings = settings,
                )
                NovaDebugLog.log(context, "NovaCloseCleanup: delete browsing data on quit running")
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        controller.clearBrowsingDataOnQuit { }
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }

        try {
            val msg = when {
                clearedTabs && clearedData ->
                    "Nova closed your tabs and cleared your browsing data."
                clearedTabs -> "Nova closed all your tabs."
                clearedData -> "Nova cleared your browsing data."
                else -> return
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
        }
    }
}
''')

# --- New file: diagnostics log ------------------------------------------------
write(BASE + "components/NovaDebugLog.kt", r'''/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Writes Nova Browser diagnostics to logcat (tag "NovaDebug") and to a plain-text
 * log file at <external files dir>/nova-debug.log so the user can inspect what the
 * app actually did without adb. Every write goes to disk asynchronously so it is
 * safe to call from lifecycle callbacks.
 */
object NovaDebugLog {
    fun log(context: Context, message: String) {
        Log.i("NovaDebug", message)
        try {
            val file = context.getExternalFilesDir(null)?.resolve("nova-debug.log") ?: return
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    file.appendText(java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()) + " " + message + "\n")
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    fun clear(context: Context) {
        try {
            context.getExternalFilesDir(null)?.resolve("nova-debug.log")?.delete()
        } catch (_: Exception) {
        }
    }
}
''')

# --- Settings.kt: the new option + internal state -----------------------------
patch(
    BASE + "utils/Settings.kt",
    """    var shouldDeleteBrowsingDataOnQuit by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_delete_browsing_data_on_quit),
        default = false,
    )""",
    """    var shouldDeleteBrowsingDataOnQuit by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_delete_browsing_data_on_quit),
        default = false,
    )

    // Nova: close all tabs the moment the app is really closed (removed from the
    // app switcher, or Quit). Chosen in Settings -> Tabs -> Close tabs.
    var closeTabsOnExit by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_close_tabs_on_exit),
        default = false,
    )

    // Internal state for the close-on-exit detection (armed when the app goes to
    // the background; consumed at the next launch by comparing the task id).
    var closeTabsOnExitArmed by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_close_tabs_on_exit_armed),
        default = false,
    )

    var closeTabsOnExitLastTask by intPreference(
        appContext.getPreferenceKey(R.string.pref_key_close_tabs_on_exit_last_task),
        default = 0,
    )""",
)

# --- preference_keys.xml ------------------------------------------------------
patch(
    "app/src/main/res/values/preference_keys.xml",
    "</resources>",
    """    <string name="pref_key_close_tabs_on_exit" translatable="false">pref_key_close_tabs_on_exit</string>
    <string name="pref_key_close_tabs_on_exit_armed" translatable="false">pref_key_close_tabs_on_exit_armed</string>
    <string name="pref_key_close_tabs_on_exit_last_task" translatable="false">pref_key_close_tabs_on_exit_last_task</string>
    <string name="pref_key_nova_adblock" translatable="false">pref_key_nova_adblock</string>
</resources>""",
)

# --- strings.xml --------------------------------------------------------------
patch(
    "app/src/main/res/values/strings.xml",
    "</resources>",
    """    <string name="close_tabs_on_exit">When the app is closed</string>
    <string name="close_tabs_on_exit_summary">Close every tab the moment Nova is closed or removed from the app switcher.</string>
    <string name="preferences_nova_adblock">Nova Ad Block</string>
    <string name="preferences_nova_adblock_summary">Block ads, trackers and any domain you add. Whitelist domains to always allow them.</string>
</resources>""",
)

# --- Tabs settings: add the "close tabs when the app is closed" radio option ---
patch(
    "app/src/main/res/xml/tabs_preferences.xml",
    """        <org.mozilla.fenix.settings.RadioButtonPreference
            android:defaultValue="false"
            android:key="@string/pref_key_close_tabs_after_one_month"
            android:title="@string/close_tabs_after_one_month" />""",
    """        <org.mozilla.fenix.settings.RadioButtonPreference
            android:defaultValue="false"
            android:key="@string/pref_key_close_tabs_after_one_month"
            android:title="@string/close_tabs_after_one_month" />

        <org.mozilla.fenix.settings.RadioButtonPreference
            android:defaultValue="false"
            android:key="@string/pref_key_close_tabs_on_exit"
            android:title="@string/close_tabs_on_exit"
            android:summary="@string/close_tabs_on_exit_summary" />""",
)
patch(
    BASE + "settings/TabsSettingsFragment.kt",
    "    private lateinit var radioOneMonth: RadioButtonPreference",
    """    private lateinit var radioOneMonth: RadioButtonPreference
    private lateinit var radioOnExit: RadioButtonPreference""",
)
patch(
    BASE + "settings/TabsSettingsFragment.kt",
    "        radioOneDay = requirePreference(R.string.pref_key_close_tabs_after_one_day)",
    """        radioOneDay = requirePreference(R.string.pref_key_close_tabs_after_one_day)
        radioOnExit = requirePreference(R.string.pref_key_close_tabs_on_exit)""",
)
patch(
    BASE + "settings/TabsSettingsFragment.kt",
    "        radioOneMonth.onClickListener(::enableInactiveTabsSetting)",
    """        radioOneMonth.onClickListener(::enableInactiveTabsSetting)
        radioOnExit.onClickListener(::disableInactiveTabsSetting)""",
)
patch(
    BASE + "settings/TabsSettingsFragment.kt",
    """        addToRadioGroup(
            radioManual,
            radioOneDay,
            radioOneMonth,
            radioOneWeek,
        )""",
    """        addToRadioGroup(
            radioManual,
            radioOneDay,
            radioOneMonth,
            radioOneWeek,
            radioOnExit,
        )""",
)

# --- HomeActivity.kt: close tabs when the app is closed -----------------------
patch(
    BASE + "HomeActivity.kt",
    "import android.app.assist.AssistContent",
    "import android.app.ActivityManager\nimport android.app.assist.AssistContent",
)
patch(
    BASE + "HomeActivity.kt",
    "import kotlinx.coroutines.Dispatchers",
    "import kotlinx.coroutines.CoroutineScope\nimport kotlinx.coroutines.Dispatchers",
)
patch(
    BASE + "HomeActivity.kt",
    "import kotlinx.coroutines.Job\n",
    "import kotlinx.coroutines.Job\nimport kotlinx.coroutines.SupervisorJob\n",
)
patch(
    BASE + "HomeActivity.kt",
    "import org.mozilla.fenix.settings.SupportUtils",
    "import org.mozilla.fenix.components.NovaCloseCleanup\n" +
    "import org.mozilla.fenix.components.NovaDebugLog\n" +
    "import org.mozilla.fenix.settings.SupportUtils",
)
patch(
    BASE + "HomeActivity.kt",
    "        checkAndExitPiP()",
    "        checkAndExitPiP()\n        consumeNovaClearTabsOnExit()",
)
patch(
    BASE + "HomeActivity.kt",
    "        super.onStop()",
    "        super.onStop()\n        armNovaClearOnExitCheck()\n        scheduleNovaClearOnCloseCheck()",
)
patch(
    BASE + "HomeActivity.kt",
    "    final override fun onStart() {",
    """    private var novaScheduledCheck: Job? = null

    /**
     * "Close tabs when the app is closed": every time the app goes to the
     * background the current task id is remembered and a short timer is started.
     * If the task is later removed from the app switcher the user really closed
     * the app, so the cleanup runs right away. If the process was killed before
     * the timer fired, the decision happens at the next launch (task id
     * comparison), where the session snapshot is dropped before it is restored
     * (the same point where the stock "Close tabs after X" option drops tabs).
     * Not for the external-app browser activity (custom tabs), which is a
     * separate task and must not close the user's tabs when it is dismissed.
     */
    private fun armNovaClearOnExitCheck() {
        if (this is ExternalAppBrowserActivity) return
        val settings = components.settings
        if (!settings.closeTabsOnExit && !settings.shouldDeleteBrowsingDataOnQuit) return
        settings.closeTabsOnExitLastTask = taskId
        settings.closeTabsOnExitArmed = true
        NovaDebugLog.log(this, "arm: armed=true task=$taskId")
    }

    @Suppress("DEPRECATION")
    private fun scheduleNovaClearOnCloseCheck() {
        // Called on every stop, AFTER the app has fully backgrounded. A short
        // while later, if the app's task is no longer in the recents list, the
        // user really closed it (removed it from the app switcher / pressed
        // "Clear all"), so the cleanup runs right away. A plain background keeps
        // the task, so nothing is cleared then. If the process is killed before
        // this check runs, consumeNovaClearTabsOnExit handles it at the next
        // launch by comparing the task id.
        if (this is ExternalAppBrowserActivity) return
        val settings = components.settings
        if (!settings.closeTabsOnExit && !settings.shouldDeleteBrowsingDataOnQuit) return
        if (!settings.closeTabsOnExitArmed) return
        val appContext = applicationContext
        val myTaskId = taskId
        novaScheduledCheck?.cancel()
        novaScheduledCheck = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            delay(5_000L)
            try {
                val am = appContext.getSystemService(ActivityManager::class.java)
                    ?: return@launch
                if (am.appTasks.any { it.taskInfo?.id == myTaskId }) {
                    NovaDebugLog.log(appContext, "delayed check: task still present - kept")
                    return@launch
                }
                val s = appContext.components.settings
                if (!s.closeTabsOnExitArmed) return@launch
                s.closeTabsOnExitArmed = false
                NovaDebugLog.log(appContext, "task removed from background - closing tabs")
                NovaCloseCleanup.run(appContext, appContext.components)
            } catch (_: Exception) {
            }
        }
    }

    private fun consumeNovaClearTabsOnExit() {
        // Runs at the start of every HomeActivity launch. Same task id as when the
        // app last stopped -> the app was only backgrounded, keep the tabs. A
        // different task id -> the app was really closed (removed from the app
        // switcher / Quit), so the tabs must not come back: the session snapshot
        // is deleted before it is restored, and the tabs are closed.
        if (this is ExternalAppBrowserActivity) return
        val settings = components.settings
        if (!settings.closeTabsOnExit && !settings.shouldDeleteBrowsingDataOnQuit) return
        if (!settings.closeTabsOnExitArmed) return
        val lastTaskId = settings.closeTabsOnExitLastTask
        if (lastTaskId == 0 || taskId == lastTaskId) {
            FenixApplication.novaPendingCleanStart = false
            settings.closeTabsOnExitArmed = false
            NovaDebugLog.log(this, "consume: same task ($taskId) - kept tabs")
            return
        }
        FenixApplication.novaPendingCleanStart = true
        if (!FenixApplication.initialSessionRestoreCompleted) {
            // The restore runs on the main thread shortly after this onCreate, so
            // wait for it before running the full cleanup (the snapshot deletion is
            // handled by restoreBrowserState).
            CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                var waited = 0L
                while (!FenixApplication.initialSessionRestoreCompleted && waited < 15000) {
                    delay(100)
                    waited += 100
                }
                if (FenixApplication.initialSessionRestoreCompleted) {
                    consumeNovaClearTabsOnExitInternal()
                }
            }
            return
        }
        consumeNovaClearTabsOnExitInternal()
    }

    private fun consumeNovaClearTabsOnExitInternal() {
        val settings = components.settings
        settings.closeTabsOnExitArmed = false
        FenixApplication.novaPendingCleanStart = false
        NovaDebugLog.log(this, "consume: new task - closing tabs")
        NovaCloseCleanup.run(this, components)
    }

    final override fun onStart() {""",
)
patch(
    BASE + "HomeActivity.kt",
    "        super.onStart()",
    "        super.onStart()\n        novaScheduledCheck?.cancel()",
)

# --- FenixApplication.kt: restore-time drop -----------------------------------
patch(
    BASE + "FenixApplication.kt",
    "        components.useCases.tabsUseCases.restore(sessionStorage, components.settings.getTabTimeout())",
    """        // Nova: if "close tabs when the app is closed" was armed and HomeActivity saw
        // a fresh task at this launch, delete the saved session snapshot BEFORE it is
        // restored so the tabs cannot come back. This is the same point where the
        // stock "Close tabs after X" option drops stale tabs. (If the restore already
        // ran before HomeActivity.onCreate on some Android versions, the flag stays
        // set and the HomeActivity cleanup handles it instead.)
        if (novaPendingCleanStart) {
            novaPendingCleanStart = false
            org.mozilla.fenix.components.NovaDebugLog.log(applicationContext, "restore: dropping saved tabs (pending clean start)")
            sessionStorage.clear()
        }
        components.useCases.tabsUseCases.restore(sessionStorage, components.settings.getTabTimeout())

        // Nova: HomeActivity decides whether this relaunch should start fresh by
        // comparing the current task id against the one saved when the app last
        // stopped (see consumeNovaClearTabsOnExit in HomeActivity.kt). We only mark
        // that the initial restore has finished here.
        initialSessionRestoreCompleted = true""",
)
patch(
    BASE + "FenixApplication.kt",
    "open class FenixApplication : Application(), Provider, ThemeProvider {",
    """open class FenixApplication : Application(), Provider, ThemeProvider {
    companion object {
        /**
         * Becomes true once the initial session restore has finished, so an in-process
         * relaunch knows the "close tabs on exit" armed flag can be consumed safely.
         */
        var initialSessionRestoreCompleted = false
            private set

        /**
         * Set by HomeActivity when the "close tabs on exit" armed flag is consumed at
         * launch and the session restore has not run yet: the saved session snapshot
         * is then deleted right before the restore (the same point where the stock
         * "Close tabs after X" option drops stale tabs).
         */
        var novaPendingCleanStart = false

        // Nova: ids of the bundled ad-blocking add-ons (installed as built-in
        // WebExtensions, see installNovaBundledExtensions).
        private const val NOVA_SHIELD_ADDON_ID = "nova-shield@nova.browser"
        private const val NOVA_UBLOCK_ADDON_ID = "uBlock0@raymondhill.net"
    }
""",
)

# --- FenixApplication.kt: install bundled ad-blocking add-ons -----------------
patch(
    BASE + "FenixApplication.kt",
    """                onUpdatePermissionRequest = components.addonUpdater::onUpdatePermissionRequest,
            )""",
    """                onUpdatePermissionRequest = components.addonUpdater::onUpdatePermissionRequest,
            )

            installNovaBundledExtensions()""",
)
patch(
    BASE + "FenixApplication.kt",
    """            logger.error("Failed to initialize web extension support", e)
        }
    }

    @VisibleForTesting""",
    """            logger.error("Failed to initialize web extension support", e)
        }
    }

    /**
     * Nova: installs the two bundled ad-blocking WebExtensions (Nova Ad Block and
     * uBlock Origin) as built-in add-ons. ensureBuiltInWebExtension is idempotent,
     * so it is safe to call on every launch; a failed install (e.g. while the engine
     * is still warming up) simply logs to NovaDebug and is retried next launch.
     */
    private fun installNovaBundledExtensions() {
        val engine = components.core.engine
        engine.installBuiltInWebExtension(
            id = NOVA_SHIELD_ADDON_ID,
            url = "resource://android/assets/extensions/nova-shield/",
            onSuccess = { org.mozilla.fenix.components.NovaDebugLog.log(applicationContext, "Nova Shield installed: ${it.id}") },
            onError = { org.mozilla.fenix.components.NovaDebugLog.log(applicationContext, "Nova Shield install error: ${it.message}") },
        )
        engine.installBuiltInWebExtension(
            id = NOVA_UBLOCK_ADDON_ID,
            url = "resource://android/assets/extensions/ublock_origin/",
            onSuccess = { org.mozilla.fenix.components.NovaDebugLog.log(applicationContext, "uBlock Origin installed: ${it.id}") },
            onError = { org.mozilla.fenix.components.NovaDebugLog.log(applicationContext, "uBlock Origin install error: ${it.message}") },
        )
    }

    @VisibleForTesting""",
)

# --- Settings: Nova Ad Block row ----------------------------------------------
patch(
    "app/src/main/res/xml/preferences.xml",
    """        <androidx.preference.Preference
            android:key="@string/pref_key_addons"
            app:iconSpaceReserved="false"
            android:title="@string/preferences_extensions" />""",
    """        <androidx.preference.Preference
            android:key="@string/pref_key_addons"
            app:iconSpaceReserved="false"
            android:title="@string/preferences_extensions" />

        <androidx.preference.Preference
            android:key="@string/pref_key_nova_adblock"
            app:iconSpaceReserved="false"
            android:title="@string/preferences_nova_adblock"
            android:summary="@string/preferences_nova_adblock_summary" />""",
)
patch(
    BASE + "settings/SettingsFragment.kt",
    """            resources.getString(R.string.pref_key_addons) -> {
                Addons.openAddonsInSettings.record(NoExtras())
                SettingsFragmentDirections.actionSettingsFragmentToAddonsFragment()
            }""",
    """            resources.getString(R.string.pref_key_addons) -> {
                Addons.openAddonsInSettings.record(NoExtras())
                SettingsFragmentDirections.actionSettingsFragmentToAddonsFragment()
            }

            resources.getString(R.string.pref_key_nova_adblock) -> {
                // Nova: open the Nova Ad Block options page (block list / allow list).
                // Falls back to the Add-ons screen if the extension is not ready yet.
                viewLifecycleOwner.lifecycleScope.launch {
                    val url = try {
                        components.addonManager
                            .getAddonByID("nova-shield@nova.browser")?.installedState?.optionsPageUrl
                    } catch (_: Exception) {
                        null
                    }
                    if (url.isNullOrEmpty()) {
                        findNavController().navigate(
                            SettingsFragmentDirections.actionSettingsFragmentToAddonsFragment(),
                        )
                    } else {
                        openInNewTab(url)
                    }
                }
                null
            }""",
)

# --- Settings: make the "Install local add-on" row visible --------------------
patch(
    "app/src/main/res/xml/preferences.xml",
    """        <androidx.preference.Preference
            android:key="@string/pref_key_install_local_addon"
            app:iconSpaceReserved="false"
            app:isPreferenceVisible="false"
            android:title="@string/preferences_install_local_extension" />""",
    """        <androidx.preference.Preference
            android:key="@string/pref_key_install_local_addon"
            app:iconSpaceReserved="false"
            android:title="@string/preferences_install_local_extension" />""",
)

print("All Nova source patches applied.")

PY

echo ">> Nova branding: done"
