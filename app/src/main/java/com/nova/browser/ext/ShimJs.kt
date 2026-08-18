package com.nova.browser.ext

/**
 * The chrome.* API shim injected into every extension context
 * (content scripts, background pages, popups). It talks to Kotlin through
 * the `novaBridge` JavascriptInterface.
 *
 * `window.__novaExtId` must be set to the running extension's id before scripts run
 * (each content-script injection and each background/popup wrapper sets it).
 */
object ShimJs {

    const val SHIM = """(function () {
  if (window.__novaApiInjected) return;
  window.__novaApiInjected = true;
  var pendingReplies = {};
  var messageListeners = [];
  var nextCbId = 0;
  function extId() { return window.__novaExtId || ''; }
  function newCbId() { return 'c' + (++nextCbId) + '_' + Math.random().toString(36).slice(2, 8); }
  window.__novaResp = function (cbId, json) {
    var fn = pendingReplies[cbId];
    delete pendingReplies[cbId];
    if (!fn) return;
    var value = null;
    try { value = JSON.parse(json); } catch (e) {}
    fn(value);
  };
  function callBridge(method, payload, cb) {
    var cbId = newCbId();
    if (cb) pendingReplies[cbId] = cb;
    try { window.novaBridge[method](extId(), JSON.stringify(payload), cbId); }
    catch (e) { delete pendingReplies[cbId]; if (cb) cb(null); }
  }
  window.__novaOnMessage = function (json, cbId) {
    var msg = null;
    try { msg = JSON.parse(json); } catch (e) {}
    var responded = false;
    var sendResponse = function (resp) {
      if (responded) return;
      responded = true;
      try { window.novaBridge.runtimeReply(extId(), cbId, JSON.stringify(resp === undefined ? null : resp)); } catch (e) {}
    };
    var async = false;
    for (var i = 0; i < messageListeners.length; i++) {
      var ret;
      try { ret = messageListeners[i](msg, {}, sendResponse); } catch (e) { console.error('Nova ext listener error:', e); }
      if (ret === true) { async = true; }
      else if (ret !== undefined && !responded) { sendResponse(ret); }
    }
    if (!responded && !async) sendResponse(null);
  };
  window.chrome = window.chrome || {};
  window.browser = window.browser || window.chrome;
  chrome.runtime = {
    get id() { return extId(); },
    lastError: undefined,
    onMessage: {
      addListener: function (fn) { messageListeners.push(fn); },
      removeListener: function (fn) {
        messageListeners = messageListeners.filter(function (f) { return f !== fn; });
      }
    },
    onInstalled: { addListener: function (fn) {} },
    sendMessage: function (msg, cb) {
      callBridge('runtimeSendMessage', msg, function (resp) { if (cb) cb(resp); });
    }
  };
  chrome.storage = {
    local: {
      get: function (keys, cb) {
        callBridge('storageGet', {}, function (full) {
          full = full || {};
          var out = {};
          if (keys == null) out = full;
          else if (typeof keys === 'string') { if (keys in full) out[keys] = full[keys]; }
          else if (Array.isArray(keys)) { for (var i = 0; i < keys.length; i++) if (keys[i] in full) out[keys[i]] = full[keys[i]]; }
          else if (typeof keys === 'object') { for (var k in keys) out[k] = (k in full) ? full[k] : keys[k]; }
          if (cb) cb(out);
        });
      },
      set: function (data, cb) { callBridge('storageSet', { data: data }, function () { if (cb) cb(); }); },
      remove: function (keys, cb) { callBridge('storageRemove', { keys: keys }, function () { if (cb) cb(); }); },
      clear: function (cb) { callBridge('storageClear', {}, function () { if (cb) cb(); }); }
    }
  };
  chrome.storage.sync = chrome.storage.local;
  chrome.tabs = {
    query: function (queryInfo, cb) {
      callBridge('tabsQuery', queryInfo, function (tabs) { if (cb) cb(tabs || []); });
    },
    get: function (id, cb) {
      callBridge('tabsQuery', { active: true }, function (tabs) { if (cb) cb((tabs && tabs[0]) || null); });
    },
    getCurrent: function (cb) {
      callBridge('tabsQuery', { active: true }, function (tabs) { if (cb) cb((tabs && tabs[0]) || null); });
    },
    create: function (opts) {
      try { window.novaBridge.tabsCreate(opts && opts.url ? String(opts.url) : ''); } catch (e) {}
    },
    update: function (id, opts) {
      if (opts && opts.url) { try { window.novaBridge.tabsUpdate(String(opts.url)); } catch (e) {} }
    }
  };
  chrome.action = {
    setTitle: function () {},
    setBadgeText: function (d) {
      try { window.novaBridge.actionSetBadge(extId(), (d && d.text) || '', ''); } catch (e) {}
    },
    setBadgeBackgroundColor: function (d) {
      try { window.novaBridge.actionSetBadgeColor(extId(), (d && d.color) || ''); } catch (e) {}
    },
    setIcon: function () {}
  };
  chrome.browserAction = chrome.action;
  chrome.contextMenus = {
    create: function () { return 0; },
    onClicked: { addListener: function () {} }
  };
  chrome.i18n = {
    getMessage: function (key) { return key; },
    getUILanguage: function () { return 'en'; }
  };
  chrome.extension = {
    getURL: function (p) { return p; },
    inIncognitoContext: false
  };
})();
"""
}
