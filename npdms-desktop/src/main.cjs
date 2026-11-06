'use strict';
const { app, BrowserWindow, ipcMain, session, Menu } = require('electron');
const { join } = require('node:path');
const { pathToFileURL } = require('node:url');
const { serverOrigin, isSameOrigin, secureWebPreferences, requireTrustedSender } = require('./security.cjs');

const shellUrl = pathToFileURL(join(__dirname, '../resources/index.html')).href;
let shellWindow;
let platformWindow;
let activeOrigin;

function denyPermissions(targetSession) {
  targetSession.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
  targetSession.setPermissionCheckHandler(() => false);
  targetSession.on('will-download', event => event.preventDefault());
}

function guardWindow(window, allowNavigation) {
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  window.webContents.on('will-navigate', (event, url) => {
    if (!allowNavigation(url)) event.preventDefault();
  });
  window.webContents.on('will-redirect', (event, url) => {
    if (!allowNavigation(url)) event.preventDefault();
  });
  window.webContents.on('will-attach-webview', event => event.preventDefault());
}

async function openPlatform(origin) {
  // A memory-only, unprivileged session: the hosted application never receives desktop IPC.
  if (platformWindow && !platformWindow.isDestroyed() && activeOrigin === origin) {
    platformWindow.focus();
    return;
  }
  if (platformWindow && !platformWindow.isDestroyed()) platformWindow.close();
  const platformSession = session.fromPartition(`npdms-platform-${require('node:crypto').randomUUID()}`);
  denyPermissions(platformSession);
  platformSession.webRequest.onBeforeRequest((details, callback) => {
    callback({ cancel: !isSameOrigin(details.url, origin) });
  });
  const window = new BrowserWindow({
    width: 1440, height: 920, minWidth: 960, minHeight: 640,
    title: 'NPDMS', show: false,
    webPreferences: { ...secureWebPreferences(), session: platformSession }
  });
  platformWindow = window;
  activeOrigin = origin;
  guardWindow(window, url => isSameOrigin(url, origin));
  window.once('ready-to-show', () => window.show());
  window.on('closed', () => {
    if (platformWindow === window) { platformWindow = null; activeOrigin = null; }
    void platformSession.clearStorageData();
  });
  try { await window.loadURL(origin); }
  catch { if (!window.isDestroyed()) window.close(); throw new Error('PLATFORM_LOAD_FAILED'); }
}

if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', () => {
    if (shellWindow) { if (shellWindow.isMinimized()) shellWindow.restore(); shellWindow.focus(); }
  });
  app.whenReady().then(async () => {
    Menu.setApplicationMenu(null);
    denyPermissions(session.defaultSession);
    shellWindow = new BrowserWindow({
      width: 1000, height: 740, minWidth: 760, minHeight: 600, title: 'NPDMS 桌面客户端',
      webPreferences: secureWebPreferences(join(__dirname, 'preload.cjs'))
    });
    guardWindow(shellWindow, url => url === shellUrl);
    ipcMain.handle('desktop:status', event => {
      requireTrustedSender(event, shellWindow, shellUrl);
      return { version: app.getVersion(), baseline: 'd4f6918d67c3f5b32559d7eba6deb6e9a847858a',
        offline: { enabled: false, reason: 'BLOCKED_BY_SPEC: 服务端离线授权与密钥策略尚未定义' },
        dac: { state: 'not-configured' } };
    });
    ipcMain.handle('desktop:open-platform', async (event, value) => {
      requireTrustedSender(event, shellWindow, shellUrl);
      try { await openPlatform(serverOrigin(value)); return { ok: true }; }
      catch (error) { return { ok: false, code: ['PLATFORM_LOAD_FAILED'].includes(error.message) ? error.message : 'INVALID_SERVER_ORIGIN' }; }
    });
    await shellWindow.loadURL(shellUrl);
  }).catch(() => { app.exit(1); });
  app.on('window-all-closed', () => app.quit());
}
