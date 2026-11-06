'use strict';
const ui = id => document.getElementById(id);
window.npdmsDesktop.status().then(status => {
  ui('offline').textContent = status.offline.reason;
  ui('dac').textContent = status.dac.state === 'not-configured' ? '尚未装配 DAC 运行包' : status.dac.state;
  ui('version').textContent = `版本 ${status.version} · 基线 ${status.baseline.slice(0, 7)}`;
}).catch(() => { ui('offline').textContent = '状态不可用，离线功能已关闭。'; ui('dac').textContent = '状态不可用'; });
ui('connect').addEventListener('submit', async event => {
  event.preventDefault(); ui('open').disabled = true; ui('connection').textContent = '正在连接…';
  try {
    const result = await window.npdmsDesktop.openPlatform(ui('origin').value.trim());
    ui('connection').textContent = result.ok ? '平台已打开。' : result.code === 'PLATFORM_LOAD_FAILED' ? '连接失败，请检查服务地址与网络后重试。' : '请输入不含路径、账户信息或参数的 HTTPS 平台地址。';
  } catch { ui('connection').textContent = '连接未完成，请重试。'; }
  finally { ui('open').disabled = false; }
});
