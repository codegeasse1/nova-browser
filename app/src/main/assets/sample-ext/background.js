chrome.runtime.onInstalled.addListener(function () {
  chrome.storage.local.set({ accentColor: '#ff6b35', highlightLinks: true });
});

chrome.runtime.onMessage.addListener(function (msg, sender, sendResponse) {
  if (msg && msg.type === 'pageLoaded') {
    var len = String(msg.title || '').length % 100;
    chrome.action.setBadgeText({ text: String(len) });
    sendResponse({ ok: true });
  }
  return true;
});
