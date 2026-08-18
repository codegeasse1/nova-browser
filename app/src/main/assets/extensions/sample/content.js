// Nova Sample extension - content script
browser.runtime.onMessage.addListener((message) => {
  if (message && message.command === "nova-toggle-dark") {
    const root = document.documentElement;
    if (root.style.filter && root.style.filter.includes("invert")) {
      root.style.filter = "";
    } else {
      root.style.filter = "invert(0.92) hue-rotate(180deg)";
      root.style.background = "#111";
    }
    return Promise.resolve({ ok: true });
  }
  return Promise.resolve({ ok: false });
});
