var colorEl = document.getElementById('color');
var linksEl = document.getElementById('links');
var statusEl = document.getElementById('status');

chrome.storage.local.get({ accentColor: '#ff6b35', highlightLinks: true }, function (data) {
  colorEl.value = data.accentColor || '#ff6b35';
  linksEl.checked = data.highlightLinks !== false;
});

function save() {
  chrome.storage.local.set({
    accentColor: colorEl.value,
    highlightLinks: linksEl.checked
  }, function () {
    statusEl.textContent = 'Saved. Refresh the page to see the change.';
  });
}

colorEl.addEventListener('change', save);
linksEl.addEventListener('change', save);
