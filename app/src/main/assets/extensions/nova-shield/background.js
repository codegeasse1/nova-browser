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
