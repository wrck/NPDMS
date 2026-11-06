'use strict';
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('npdmsDesktop', Object.freeze({
  status: () => ipcRenderer.invoke('desktop:status'),
  openPlatform: origin => ipcRenderer.invoke('desktop:open-platform', origin)
}));
