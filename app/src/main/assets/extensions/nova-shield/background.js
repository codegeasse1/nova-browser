"use strict";

/* Nova Shield — self-contained ad/tracker blocker.
 * Rules are matched entirely inside the extension (bundled filter lists), so
 * no page request ever waits on a native round-trip. */

const REQUEST_TYPES = [
  "script", "image", "xmlhttprequest", "stylesheet", "font",
  "object", "other", "sub_frame", "beacon"
];

const TIMEOUT_MS = 1500;

let ready = false;
let level = "standard";
let blockList = [];
let allowList = [];

const exceptions = [];
const hostRules = new Map();
const exactRules = [];
const prefixRules = [];
const regexRules = [];
const plainRules = [];

function hostOf(url) {
  try { return new URL(url).hostname.toLowerCase(); } catch (e) { return null; }
}

function pathOf(url) {
  try {
    const u = new URL(url);
    return u.pathname + (u.search ? u.search : "");
  } catch (e) { return url; }
}

function hostListMatches(host, list) {
  let h = host;
  while (h) {
    if (list.indexOf(h) !== -1) return true;
    const dot = h.indexOf(".");
    if (dot < 0 || dot === h.length - 1) break;
    h = h.slice(dot + 1);
  }
  return false;
}

function pageHostInList(host, list) {
  for (const x of list) {
    if (host === x || host.endsWith("." + x)) return true;
  }
  return false;
}

function matchRule(r, url, host, pageHost, thirdParty) {
  if (r.thirdPartyOnly && !thirdParty) return false;
  if (r.firstPartyOnly && thirdParty) return false;
  if (r.allowedPages.length && !pageHostInList(pageHost, r.allowedPages)) return false;
  if (pageHostInList(pageHost, r.excludedPages)) return false;

  switch (r.kind) {
    case "host": {
      if (host !== r.domainKey && !host.endsWith("." + r.domainKey)) return false;
      if (r.pathPart === null) return true;
      return pathOf(url).startsWith(r.pathPart) || url.indexOf(r.domainKey + r.pathPart) !== -1;
    }
    case "exact": return url === r.pattern;
    case "prefix": return url.startsWith(r.pattern);
    case "regex": return r.regex.test(url);
    case "plain": return r.pattern.length >= 5 && url.indexOf(r.pattern) !== -1;
  }
  return false;
}

function matchesList(rules, url, host, pageHost, thirdParty) {
  for (const r of rules) {
    if (matchRule(r, url, host, pageHost, thirdParty)) return true;
  }
  return false;
}

function shouldBlock(url, pageUrl) {
  if (!ready || !url) return false;
  if (url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("about:") ||
      url.startsWith("chrome:") || url.startsWith("resource:") || url.startsWith("moz-extension:")) return false;

  const host = hostOf(url);
  if (!host) return false;
  const pageHost = hostOf(pageUrl) || host;
  const thirdParty = host !== pageHost && !host.endsWith("." + pageHost) && !pageHost.endsWith("." + host);

  if (allowList.length && (hostListMatches(pageHost, allowList) || hostListMatches(host, allowList))) return false;
  if (blockList.length && hostListMatches(host, blockList)) return true;

  if (matchesList(exceptions, url, host, pageHost, thirdParty)) return false;

  let h = host;
  while (h) {
    const rules = hostRules.get(h);
    if (rules && matchesList(rules, url, host, pageHost, thirdParty)) return true;
    const dot = h.indexOf(".");
    if (dot < 0 || dot === h.length - 1) break;
    h = h.slice(dot + 1);
  }

  if (matchesList(exactRules, url, host, pageHost, thirdParty)) return true;
  if (matchesList(prefixRules, url, host, pageHost, thirdParty)) return true;
  if (matchesList(regexRules, url, host, pageHost, thirdParty)) return true;
  if (matchesList(plainRules, url, host, pageHost, thirdParty)) return true;
  return false;
}

function buildRule(pattern, options) {
  const opts = options.split(",").map(s => s.trim()).filter(Boolean);
  const thirdParty = opts.indexOf("third-party") !== -1 || opts.indexOf("3p") !== -1;
  const firstParty = opts.indexOf("first-party") !== -1;
  let allowedPages = [], excludedPages = [];
  for (const d of opts) {
    const m = d.match(/^domain[=:](.+)$/);
    if (m) {
      const list = m[1].split("|").map(s => s.trim().toLowerCase()).filter(Boolean);
      allowedPages = list.filter(s => s.indexOf("~") !== 0);
      excludedPages = list.filter(s => s.indexOf("~") === 0).map(s => s.slice(1));
    }
  }

  let p = pattern.trim();
  if (p.length < 3) return null;

  if (p[0] === "/" && p.length > 2 && p[p.length - 1] === "/" && p[p.length - 2] !== "\\") {
    const inner = p.slice(1, -1);
    let re;
    try { re = new RegExp(inner); } catch (e) { return null; }
    return { kind: "regex", pattern: inner, domainKey: null, pathPart: null, regex: re,
             thirdPartyOnly: thirdParty, firstPartyOnly: firstParty, allowedPages: allowedPages, excludedPages: excludedPages };
  }

  if (p.startsWith("||")) {
    let host = p.slice(2);
    let pathPart = null;
    const slash = host.indexOf("/");
    if (slash >= 0) { pathPart = host.slice(slash); host = host.slice(0, slash); }
    host = host.replace(/\^+$/, "").replace(/\|+$/, "").toLowerCase();
    if (!host || host.length < 3) return null;
    pathPart = pathPart === null ? null : pathPart.replace(/\^+$/, "");
    return { kind: "host", pattern: p, domainKey: host, pathPart: pathPart, regex: null,
             thirdPartyOnly: thirdParty, firstPartyOnly: firstParty, allowedPages: allowedPages, excludedPages: excludedPages };
  }

  const exact = p[0] === "|" && p[p.length - 1] === "|";
  if (exact) {
    const inner = p.slice(1, -1);
    if (inner.length < 3) return null;
    return { kind: "exact", pattern: inner, domainKey: null, pathPart: null, regex: null,
             thirdPartyOnly: thirdParty, firstPartyOnly: firstParty, allowedPages: allowedPages, excludedPages: excludedPages };
  }
  if (p[0] === "|") {
    const inner = p.slice(1);
    if (inner.length < 3) return null;
    return { kind: "prefix", pattern: inner, domainKey: null, pathPart: null, regex: null,
             thirdPartyOnly: thirdParty, firstPartyOnly: firstParty, allowedPages: allowedPages, excludedPages: excludedPages };
  }
  return { kind: "plain", pattern: p, domainKey: null, pathPart: null, regex: null,
           thirdPartyOnly: thirdParty, firstPartyOnly: firstParty, allowedPages: allowedPages, excludedPages: excludedPages };
}

function clearRules() {
  exceptions.length = 0;
  hostRules.clear();
  exactRules.length = 0;
  prefixRules.length = 0;
  regexRules.length = 0;
  plainRules.length = 0;
}

function parseList(text) {
  const lines = text.split("\n");
  for (let k = 0; k < lines.length; k++) {
    let line = lines[k].trim();
    if (!line || line[0] === "!" || line[0] === "[") continue;
    if (line.indexOf("##") !== -1 || line.indexOf("#@#") !== -1 || line.indexOf("#?#") !== -1) continue;

    let isException = false;
    if (line[0] === "@") {
      if (line.indexOf("@@") !== 0) continue;
      isException = true;
      line = line.slice(2);
    }

    let options = "";
    const dollar = line.lastIndexOf("$");
    if (dollar > 0) {
      const opt = line.slice(dollar + 1);
      if (opt && opt.length < 120 && opt.indexOf("://") === -1) {
        options = opt;
        line = line.slice(0, dollar);
      }
    }

    const rule = buildRule(line, options);
    if (!rule) continue;
    if (isException) {
      exceptions.push(rule);
    } else if (rule.kind === "host") {
      let arr = hostRules.get(rule.domainKey);
      if (!arr) { arr = []; hostRules.set(rule.domainKey, arr); }
      arr.push(rule);
    } else if (rule.kind === "exact") {
      exactRules.push(rule);
    } else if (rule.kind === "prefix") {
      prefixRules.push(rule);
    } else if (rule.kind === "regex") {
      regexRules.push(rule);
    } else {
      plainRules.push(rule);
    }
  }
}

async function loadLists() {
  const names = level === "strict"
    ? ["easylist.txt", "easyprivacy.txt", "annoyances.txt", "nova-extra.txt"]
    : ["easylist.txt", "easyprivacy.txt", "nova-extra.txt"];
  clearRules();
  for (const name of names) {
    try {
      const res = await fetch(browser.runtime.getURL("filters/" + name));
      if (res.ok) {
        parseList(await res.text());
      }
    } catch (e) { /* skip */ }
  }
  ready = true;
}

async function syncConfig() {
  try {
    const cfg = await Promise.race([
      browser.runtime.sendNativeMessage("nova", { type: "getConfig" }),
      new Promise(res => setTimeout(() => res(null), TIMEOUT_MS))
    ]);
    if (!cfg) return;
    if (cfg.level !== level) {
      level = cfg.level || "standard";
      ready = false;
      loadLists();
    }
    blockList = cfg.blockedDomains || [];
    allowList = cfg.whitelistedDomains || [];
  } catch (e) { /* native side unavailable */ }
}

const pendingBlocks = new Map();

function reportStats() {
  if (pendingBlocks.size === 0) return;
  const tabs = [...pendingBlocks.entries()];
  pendingBlocks.clear();
  browser.runtime.sendNativeMessage("nova", { type: "stats", tabs: tabs }).catch(() => {});
}

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    if (!ready) return undefined;
    const pageUrl = details.documentUrl || details.originUrl || details.initiator || "";
    if (shouldBlock(details.url, pageUrl)) {
      const tabId = typeof details.tabId === "number" ? details.tabId : -1;
      if (tabId > 0) pendingBlocks.set(tabId, (pendingBlocks.get(tabId) || 0) + 1);
      return { cancel: true };
    }
    return undefined;
  },
  { urls: ["<all_urls>"], types: REQUEST_TYPES },
  ["blocking"]
);

syncConfig();
loadLists();
setInterval(syncConfig, 3000);
setInterval(reportStats, 2000);
