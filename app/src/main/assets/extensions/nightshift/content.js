(function(){
  var css = 'html{filter:invert(1) hue-rotate(180deg) contrast(.9) saturate(.85) !important;background:#0e0d16}img,video,picture,canvas,[style*="background-image"]{filter:invert(1) hue-rotate(180deg) !important}';
  var s = document.createElement('style');
  s.setAttribute('data-nova-ext','nightshift');
  s.textContent = css;
  document.documentElement.appendChild(s);
})();
