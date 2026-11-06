'use strict';
const ui = id => document.getElementById(id), api = window.npdmsDesktop;
let busy = false;
const errors = { INVALID_SERVER_ORIGIN: '请输入有效的 HTTPS 服务地址。', HTTPS_ORIGIN_REQUIRED: '请输入不含路径、账户信息或参数的 HTTPS 服务地址。', AUTH_EXPIRED: '本轮15分钟测试授权已过期，草稿和队列已锁定。', STORE_LOCKED: '试验数据仍被其他进程占用。', CLOCK_UNTRUSTED: '检测到时钟回退，已锁定试验数据。', FRONTEND_NOT_BUILT: '尚未装配内置 Vue 前端，请先完成前端构建。', PLATFORM_BUSY: '平台正在打开，请稍后。', OPERATION_UNAVAILABLE: '操作不可用。请检查 Linux 系统密钥环和运行环境；不会退回明文存储。' };
const explain = code => errors[code] || `操作未完成（${code || 'UNKNOWN'}）。数据未被自动重置。`;
async function refreshStatus() {
  const result = await api.status(); if (!result.ok) throw new Error(result.code);
  const status = result.data;
  ui('offline').textContent = status.offline.reason;
  ui('dac').textContent = status.dac.state === 'not-configured' ? '尚未完成 DAC Linux 运行包与身份装配' : status.dac.state;
  ui('version').textContent = `版本 ${status.version} · 基线 ${status.baseline.slice(0, 7)} · ${status.frontend.bundled ? '内置 Vue 已装配' : 'Vue 尚未装配'}`;
  if (status.trial.state === 'active') ui('trial-state').textContent = `仅测试 · ${status.trial.pendingCount}项待处理 · 到期 ${new Date(status.trial.expiresAt).toLocaleTimeString()}`;
  else if (status.trial.state === 'expired') ui('trial-state').textContent = explain('AUTH_EXPIRED');
  else if (status.trial.state === 'not-started') ui('trial-state').textContent = status.trial.keyVaultAvailable ? '系统密钥环可用，试验尚未开始。' : '系统密钥环不可用，试验保持关闭。';
}
async function refreshTrial() {
  const result = await api.trialList();
  if (!result.ok) { ui('trial-result').textContent = explain(result.code); await refreshStatus(); return; }
  ui('trial-controls').hidden = false;
  const { drafts, queue } = result.data;
  ui('trial-records').replaceChildren(); ui('trial-queue').replaceChildren();
  const summary = document.createElement('li'); summary.textContent = `已加密保存 ${drafts.length} 条虚构草稿。接受结果仅为本地模拟，不代表业务完成。`; ui('trial-records').append(summary);
  for (const op of queue) {
    const item = document.createElement('li'), text = document.createElement('span');
    text.textContent = `测试操作 ${op.id.slice(0, 8)} · ${op.state} · 尝试 ${op.attempts} 次`; item.append(text);
    if (op.state === 'conflict') for (const [action, label] of [['retryAccepted', '明确重建并重试'], ['discard', '丢弃测试操作']]) {
      const button = document.createElement('button'); button.textContent = label; button.className = 'secondary';
      button.addEventListener('click', () => trialAction(() => api.trialResolve({ id: op.id, action }))); item.append(button);
    }
    ui('trial-queue').append(item);
  }
  await refreshStatus();
}
async function trialAction(operation) {
  if (busy) return; busy = true;
  for (const button of document.querySelectorAll('.trial button')) button.disabled = true;
  try {
    const result = await operation();
    ui('trial-result').textContent = result.ok ? `本地模拟结果：${result.data?.state || result.data?.action || '已保存'}。未发起任何业务网络请求。` : explain(result.code);
    await refreshTrial();
  } catch { ui('trial-result').textContent = '试验操作未完成，请刷新状态。'; }
  finally { busy = false; for (const button of document.querySelectorAll('.trial button')) button.disabled = false; }
}
ui('connect').addEventListener('submit', async event => {
  event.preventDefault(); if (ui('open').disabled) return;
  ui('open').disabled = true; ui('connection').textContent = '正在打开内置平台…';
  try { const result = await api.openPlatform(ui('origin').value.trim()); ui('connection').textContent = result.ok ? '内置平台已打开。业务登录仍由 NPDMS 服务验证。' : explain(result.code); }
  catch { ui('connection').textContent = '打开未完成，请检查服务地址后重试。'; }
  finally { ui('open').disabled = false; }
});
ui('trial-start').addEventListener('click', async () => {
  if (ui('trial-start').disabled) return;
  ui('trial-start').disabled = true;
  try {
    const result = await api.trialStart();
    ui('trial-state').textContent = result.ok ? '仅合成测试数据。' : explain(result.code);
    if (result.ok) { ui('trial-controls').hidden = false; await refreshTrial(); }
  } catch { ui('trial-state').textContent = '试验未能开始，已保持关闭。'; }
  finally { ui('trial-start').disabled = false; }
});
ui('trial-add').addEventListener('click', () => trialAction(() => api.trialAdd({ fixtureId: 'draft-alpha', scenario: ui('scenario').value })));
ui('trial-sync').addEventListener('click', () => trialAction(() => api.trialSync()));
ui('trial-refresh').addEventListener('click', () => refreshTrial());
refreshStatus().catch(() => { ui('offline').textContent = '状态不可用，真实离线功能保持关闭。'; });
