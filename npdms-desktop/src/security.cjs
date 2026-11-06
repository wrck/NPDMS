'use strict';

function serverOrigin(value) {
  if (typeof value !== 'string' || value.length > 2048) throw new Error('INVALID_SERVER_ORIGIN');
  let url;
  try { url = new URL(value); } catch { throw new Error('INVALID_SERVER_ORIGIN'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('HTTPS_ORIGIN_REQUIRED');
  }
  return url.origin;
}

function isSameOrigin(value, origin) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password && url.origin === origin;
  } catch { return false; }
}

function secureWebPreferences(preload) {
  return {
    ...(preload ? { preload } : {}),
    nodeIntegration: false,
    nodeIntegrationInWorker: false,
    nodeIntegrationInSubFrames: false,
    contextIsolation: true,
    sandbox: true,
    webSecurity: true,
    allowRunningInsecureContent: false,
    webviewTag: false,
    navigateOnDragDrop: false,
    spellcheck: false
  };
}

function requireTrustedSender(event, window, expectedUrl) {
  if (!window || window.isDestroyed() || event.sender !== window.webContents ||
      event.senderFrame !== window.webContents.mainFrame || event.senderFrame.url !== expectedUrl) {
    throw new Error('UNTRUSTED_IPC_SENDER');
  }
}

module.exports = { serverOrigin, isSameOrigin, secureWebPreferences, requireTrustedSender };
