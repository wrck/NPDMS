'use strict';
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('npdmsDesktop', Object.freeze({
  status: () => ipcRenderer.invoke('desktop:status'),
  openPlatform: origin => ipcRenderer.invoke('desktop:open-platform', origin),
  trialStart: () => ipcRenderer.invoke('desktop:trial-start'),
  trialList: () => ipcRenderer.invoke('desktop:trial-list'),
  trialAdd: input => ipcRenderer.invoke('desktop:trial-add', input),
  trialSync: () => ipcRenderer.invoke('desktop:trial-sync'),
  trialResolve: input => ipcRenderer.invoke('desktop:trial-resolve', input)
}));
