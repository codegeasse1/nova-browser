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
