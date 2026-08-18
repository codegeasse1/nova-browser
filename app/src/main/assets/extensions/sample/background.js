// Nova Sample extension - background script
// Kept minimal: the popup talks to the content script directly.
browser.runtime.onInstalled.addListener(() => {
  console.log("[Nova Sample] installed");
});
