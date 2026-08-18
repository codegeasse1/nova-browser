(function(){
  if (window.__novaStoreHook) return;
  window.__novaStoreHook = true;
  var ID_RE = /\/detail\/[^\/]+\/([a-p]{32})/;
  function storeId(){ var m = location.pathname.match(ID_RE); return m ? m[1] : null; }
  function allElements(root, out){
    if (!root || !root.querySelectorAll) return out;
    var kids = root.querySelectorAll('button,a,[role="button"],span');
    for (var i=0;i<kids.length;i++) out.push(kids[i]);
    for (var j=0;j<out.length;j++) if (out[j].shadowRoot) allElements(out[j].shadowRoot, out);
    return out;
  }
  function findButton(){
    var els = allElements(document, []);
    for (var i=0;i<els.length;i++){
      var el = els[i];
      var txt = (el.innerText || el.textContent || '').trim();
      if (/^add to (chrome|brave|edge|opera|vivaldi|chromium|desktop)/i.test(txt)) return el;
    }
    return null;
  }
  function hook(){
    if (!storeId()) return;
    var btn = findButton();
    if (!btn) return;
    var target = btn;
    while (target && !/^(BUTTON|A|INPUT)$/.test(target.tagName)) target = target.parentElement;
    if (!target) target = btn;
    target.__novaHook = true;
    target.addEventListener('click', function(e){
      var id = storeId();
      if (!id) return;
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      if (window.NovaAndroid && window.NovaAndroid.installFromStore){
        window.NovaAndroid.installFromStore(id, document.title || '');
      }
    }, true);
    try {
      if (/^add to /i.test(target.textContent)) target.textContent = 'Add to Nova';
      else if (/^add to /i.test(btn.textContent)) btn.textContent = 'Add to Nova';
    } catch(_) {}
  }
  hook();
  setInterval(hook, 700);
})();
