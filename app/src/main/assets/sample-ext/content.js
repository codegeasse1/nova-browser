(function () {
  chrome.storage.local.get({ accentColor: '#ff6b35', highlightLinks: true }, function (data) {
    var accent = data.accentColor || '#ff6b35';
    if (data.highlightLinks !== false) {
      var style = document.createElement('style');
      style.textContent = 'a { color: ' + accent + ' !important; text-decoration: underline !important; }';
      (document.head || document.documentElement).appendChild(style);
    }
  });

  var title = (document.title || '').trim();
  chrome.runtime.sendMessage({ type: 'pageLoaded', title: title, url: location.href }, function (resp) {
    if (resp && resp.ok) console.log('[Nova sample] background acknowledged.');
  });
})();
