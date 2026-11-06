'use strict';
const { app, BrowserWindow, ipcMain, session, Menu, safeStorage } = require('electron');
const { existsSync } = require('node:fs');
const { join } = require('node:path');
const { pathToFileURL } = require('node:url');
const { randomUUID } = require('node:crypto');
const { serverOrigin, isSameOrigin, secureWebPreferences, requireTrustedSender } = require('./security.cjs');
const { createPlatformHandler, isAllowedPlatformRequest } = require('./platform.cjs');
const { LinuxKeyVault } = require('./key-store.cjs');
const { loadMasterKey } = require('./dac/key-vault.cjs');
const { createDacRuntime, DAC_ERROR_CODES } = require('./dac/runtime.cjs');
const { createWindowsServiceRuntime, WINDOWS_ERROR_CODES } = require('./dac/windows-service.cjs');
const hostPlatform = require('node:process').platform;
const { createDacBridge } = require('./dac/bridge.cjs');
const { createTrialSession } = require('./offline/trial.cjs');
const { recoverAbandonedLock } = require('./offline/index.cjs');
const shellUrl = pathToFileURL(join(__dirname, '../resources/index.html')).href;
const frontendDirectory = join(__dirname, '../resources/frontend');
let shellWindow, platformWindow, activeOrigin, trialSession, trialStarting;
let platformOpening = false;
let dacRuntime, shutdownInProgress = false, shutdownAllowed = false;

function denyPermissions(targetSession) {
  targetSession.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
  targetSession.setPermissionCheckHandler(() => false);
  targetSession.on('will-download', event => event.preventDefault());
}
function guardWindow(window, allowNavigation) {
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  for (const name of ['will-navigate', 'will-redirect']) window.webContents.on(name, (event, url) => {
    if (!allowNavigation(url)) event.preventDefault();
  });
  window.webContents.on('will-attach-webview', event => event.preventDefault());
}
async function openPlatform(origin) {
  if (!existsSync(join(frontendDirectory, 'index.html'))) throw new Error('FRONTEND_NOT_BUILT');
  if (platformOpening) throw new Error('PLATFORM_BUSY');
  if (platformWindow && !platformWindow.isDestroyed() && activeOrigin === origin) { platformWindow.focus(); return; }
  platformOpening = true;
  let window;
  try {
    if (platformWindow && !platformWindow.isDestroyed()) platformWindow.close();
    const platformSession = session.fromPartition(`npdms-platform-${randomUUID()}`);
    denyPermissions(platformSession);
    platformSession.webRequest.onBeforeRequest((details, callback) => {
      callback({ cancel: !isAllowedPlatformRequest(details.url, origin, details.resourceType) });
    });
    // Real packaged Vue assets retain the configured HTTPS origin. Only the
    // existing API namespaces are proxied; remote TLS is verified by Node fetch.
    const dacHandler = createDacBridge({ origin, withOwnedContext: (expected, use) => dacRuntime.withOwnedContext(expected, use) });
    platformSession.protocol.handle('https', createPlatformHandler({ origin, frontendDirectory, dacHandler }));
    window = new BrowserWindow({ width: 1440, height: 920, minWidth: 960, minHeight: 640,
      title: 'NPDMS', show: false, webPreferences: { ...secureWebPreferences(), session: platformSession } });
    platformWindow = window; activeOrigin = origin;
    guardWindow(window, url => isSameOrigin(url, origin));
    window.once('ready-to-show', () => window.show());
    window.on('closed', () => {
      if (platformWindow === window) { platformWindow = null; activeOrigin = null; }
      platformSession.protocol.unhandle('https');
      void platformSession.clearStorageData();
    });
    await window.loadURL(origin);
  } catch (error) {
    if (window && !window.isDestroyed()) window.close();
    throw error;
  } finally { platformOpening = false; }
}
function keyVault() { return new LinuxKeyVault({ directory: join(app.getPath('userData'), 'keys'), safeStorage }); }
async function startTrial() {
  if (trialSession) return trialSession.status();
  if (trialStarting) return trialStarting;
  trialStarting = (async () => {
    const directory = join(app.getPath('userData'), 'synthetic-trial-v1');
    const vault = keyVault();
    const key = await loadMasterKey(vault, 'synthetic-trial-v1', { create: !existsSync(directory) });
    try {
      const file = join(directory, 'synthetic-trial-v1.vault');
      if (existsSync(`${file}.lock`)) recoverAbandonedLock(file); // App single-instance lock is held.
      trialSession = createTrialSession({ directory, key });
      return trialSession.status();
    } finally { key.fill(0); }
  })();
  try { return await trialStarting; } finally { trialStarting = null; }
}
const SAFE_CODES = new Set([...DAC_ERROR_CODES, ...WINDOWS_ERROR_CODES, 'AUTH_EXPIRED', 'CLOCK_UNTRUSTED', 'STORE_LOCKED', 'STORE_CORRUPT',
  'TRIAL_INPUT_REJECTED', 'TRIAL_CAPACITY_EXCEEDED', 'TRIAL_LEASE_MISSING', 'TRIAL_LEASE_INVALID',
  'SYNC_BUSY', 'NOT_BLOCKED', 'RECOVERY_REQUIRED', 'FRONTEND_NOT_BUILT', 'PLATFORM_BUSY', 'INVALID_SERVER_ORIGIN', 'HTTPS_ORIGIN_REQUIRED']);
function safeCode(error) { return SAFE_CODES.has(error?.code || error?.message) ? (error.code || error.message) : 'OPERATION_UNAVAILABLE'; }
function registerIpc(channel, operation) {
  ipcMain.handle(channel, async (event, input) => {
    requireTrustedSender(event, shellWindow, shellUrl);
    try { return { ok: true, data: await operation(input) }; }
    catch (error) { return { ok: false, code: safeCode(error) }; }
  });
}
if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', () => { if (shellWindow) { if (shellWindow.isMinimized()) shellWindow.restore(); shellWindow.focus(); } });
  app.whenReady().then(async () => {
    Menu.setApplicationMenu(null); denyPermissions(session.defaultSession);
    dacRuntime = hostPlatform === 'win32' ? createWindowsServiceRuntime({
      helper: join(__dirname, '../resources/windows-service/NpdmsServiceBridge.exe')
    }) : createDacRuntime({ resourcesDirectory: join(__dirname, '../resources/dac'),
      dataRoot: join(app.getPath('userData'), 'dac-service-v1'), vault: keyVault() });
    shellWindow = new BrowserWindow({ width: 1100, height: 900, minWidth: 800, minHeight: 650,
      title: 'NPDMS 桌面客户端', webPreferences: secureWebPreferences(join(__dirname, 'preload.cjs')) });
    guardWindow(shellWindow, url => url === shellUrl);
    // Retain the control window when an owned DAC process has not exited yet.
    shellWindow.on('close', event => { if (!shutdownAllowed) { event.preventDefault(); app.quit(); } });
    registerIpc('desktop:status', () => ({ version: app.getVersion(), baseline: 'd4f6918d67c3f5b32559d7eba6deb6e9a847858a',
      frontend: { bundled: existsSync(join(frontendDirectory, 'index.html')) },
      offline: { enabled: false, reason: '真实离线业务未启用：等待服务端授权与密钥策略。' },
      trial: trialSession ? trialSession.status() : { state: 'not-started', keyVaultAvailable: keyVault().isAvailable() },
      dac: dacRuntime.status() }));
    registerIpc('desktop:open-platform', value => openPlatform(serverOrigin(value)));
    registerIpc('desktop:dac-start', value => {
      const origin = serverOrigin(value);
      return dacRuntime.start({ serverUrl: origin, webOrigin: origin });
    });
    registerIpc('desktop:dac-stop', () => dacRuntime.stop());
    registerIpc('desktop:trial-start', () => startTrial());
    for (const [channel, method] of [['list', 'list'], ['add', 'addDraft'], ['sync', 'syncSimulated'], ['resolve', 'resolveConflict']]) {
      registerIpc(`desktop:trial-${channel}`, input => {
        if (!trialSession) throw new Error('OPERATION_UNAVAILABLE');
        return trialSession[method](input);
      });
    }
    await shellWindow.loadURL(shellUrl);
  }).catch(() => { app.exit(1); });
  app.on('before-quit', event => {
    if (shutdownAllowed) return;
    event.preventDefault();
    if (shutdownInProgress) return;
    shutdownInProgress = true;
    Promise.resolve().then(() => dacRuntime?.stop()).then(status => {
      if (status?.state === 'stopping' || status?.canStop === true) { shellWindow?.show(); return; }
      try { trialSession?.close(); } catch { /* Durable state remains recoverable after interruption. */ }
      shutdownAllowed = true;
      app.quit();
    }).catch(() => { shellWindow?.show(); }).finally(() => { shutdownInProgress = false; });
  });
  app.on('window-all-closed', () => app.quit());
}
