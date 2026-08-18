// Nova Sample extension - popup script
document.getElementById("toggle").addEventListener("click", async () => {
  const tabs = await browser.tabs.query({ active: true, currentWindow: true });
  const tab = tabs[0];
  if (tab && tab.id != null) {
    await browser.tabs.sendMessage(tab.id, { command: "nova-toggle-dark" });
  }
  window.close();
});
