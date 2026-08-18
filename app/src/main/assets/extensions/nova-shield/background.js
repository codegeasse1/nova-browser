"use strict";

const REQUEST_TYPES = [
  "script", "image", "xmlhttprequest", "stylesheet", "media", "font",
  "object", "other", "sub_frame", "beacon"
];

const TIMEOUT_MS = 1500;

browser.webRequest.onBeforeRequest.addListener(
  async (details) => {
    try {
      const pageUrl = details.documentUrl || details.initiator || details.originUrl || "";
      const tabId = typeof details.tabId === "number" ? details.tabId : -1;
      const res = await Promise.race([
        browser.runtime.sendNativeMessage("nova", {
          type: "check",
          url: details.url,
          pageUrl: pageUrl,
          tabId: tabId
        }),
        new Promise((resolve) => setTimeout(() => resolve(undefined), TIMEOUT_MS))
      ]);
      if (res && res.block) {
        return { cancel: true };
      }
    } catch (e) {
      // Native side unavailable or error — allow the request.
    }
    return undefined;
  },
  { urls: ["<all_urls>"], types: REQUEST_TYPES },
  ["blocking"]
);
