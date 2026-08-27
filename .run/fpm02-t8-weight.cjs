const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const baseUrl = 'http://localhost:18081';
const evidenceDir = path.join(__dirname, 'fpm02-t8');
const assert = (condition, message) => { if (!condition) throw new Error(message); };

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const consoleMessages = [];
  const failedRequests = [];
  const errorResponses = [];
  page.on('console', (message) => {
    if (message.type() === 'error' || message.type() === 'warning') {
      consoleMessages.push({ type: message.type(), text: message.text() });
    }
  });
  page.on('requestfailed', (request) => failedRequests.push({ url: request.url(), failure: request.failure()?.errorText }));
  page.on('response', (response) => {
    if (response.status() >= 400) errorResponses.push({ url: response.url(), status: response.status() });
  });

  const moveCurrentProject = async (projectId, targetName) => {
    await page.goto(`${baseUrl}/pms/project-management/project-master-detail?projectId=${projectId}`, { waitUntil: 'networkidle' });
    await page.getByRole('button', { name: '项目树', exact: true }).click();
    await page.getByRole('button', { name: '子树移动', exact: true }).click();
    const dialog = page.locator('.el-dialog').filter({ hasText: '目标父项目' });
    const input = dialog.locator('.el-select__input');
    await dialog.getByText('请选择目标父项目', { exact: true }).click();
    await input.fill(targetName);
    await page.getByText(targetName, { exact: true }).last().click();
    const responsePromise = page.waitForResponse((response) =>
      response.url().includes(`/admin-api/pms/projects/${projectId}/actions/move`) && response.request().method() === 'POST');
    await dialog.getByRole('button', { name: '保存', exact: true }).click();
    const payload = await (await responsePromise).json();
    assert(payload.code === 0, `move ${projectId} failed: ${JSON.stringify(payload)}`);
  };

  await page.goto(baseUrl, { waitUntil: 'networkidle' });
  await page.locator('input[placeholder="请输入用户名"]').first().fill(process.env.NPDMS_E2E_USERNAME);
  await page.locator('input[placeholder="请输入密码"]').first().fill(process.env.NPDMS_E2E_PASSWORD);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL((url) => !url.pathname.includes('/login'));

  await moveCurrentProject(920007, '华东交付示例根项目');
  await page.goto(`${baseUrl}/pms/project-management/project-master-detail?projectId=920001`, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: '进度汇总', exact: true }).click();
  await page.getByRole('button', { name: '设置权重', exact: true }).click();
  const weightDialog = page.locator('.el-dialog').filter({ hasText: '设置直接子项目权重' });
  const inputs = weightDialog.locator('.el-input-number input');
  assert(await inputs.count() === 3, 'weight dialog did not cover all three direct children');
  await weightDialog.locator('.el-table__row').filter({ hasText: '浏览器验收子项目' }).locator('input').fill('20');
  await weightDialog.locator('.el-table__row').filter({ hasText: '上海办事处交付' }).locator('input').fill('50');
  await weightDialog.locator('.el-table__row').filter({ hasText: '江苏办事处交付' }).locator('input').fill('30');
  const weightsResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/admin-api/pms/projects/920001/child-weights') && response.request().method() === 'PUT');
  const progressResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/admin-api/pms/projects/920001/progress') && response.request().method() === 'GET');
  await weightDialog.getByRole('button', { name: '整组生效', exact: true }).click();
  const weightsPayload = await (await weightsResponsePromise).json();
  assert(weightsPayload.code === 0, `weights update failed: ${JSON.stringify(weightsPayload)}`);
  await progressResponsePromise;
  await page.getByText('直接子项目权重已整组生效', { exact: true }).waitFor();
  await weightDialog.waitFor({ state: 'hidden' });
  let body = await page.locator('body').innerText();
  assert(body.includes('34%'), `expected 34% aggregate after 20/50/30 weights: ${body.slice(-1200)}`);
  await page.screenshot({ path: path.join(evidenceDir, '07-manual-weights.png'), fullPage: true });

  await moveCurrentProject(920007, '江苏办事处交付');
  await page.goto(`${baseUrl}/pms/project-management/project-master-detail?projectId=920001`, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: '进度汇总', exact: true }).click();
  await page.getByRole('button', { name: '设置权重', exact: true }).click();
  const restoreDialog = page.locator('.el-dialog').filter({ hasText: '设置直接子项目权重' });
  await restoreDialog.locator('.el-table__row').filter({ hasText: '上海办事处交付' }).locator('input').fill('60');
  await restoreDialog.locator('.el-table__row').filter({ hasText: '江苏办事处交付' }).locator('input').fill('40');
  const restoreResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/admin-api/pms/projects/920001/child-weights') && response.request().method() === 'PUT');
  const restoredProgressPromise = page.waitForResponse((response) =>
    response.url().includes('/admin-api/pms/projects/920001/progress') && response.request().method() === 'GET');
  await restoreDialog.getByRole('button', { name: '整组生效', exact: true }).click();
  assert((await (await restoreResponsePromise).json()).code === 0, 'restoring 60/40 weights failed');
  await restoredProgressPromise;
  await restoreDialog.waitFor({ state: 'hidden' });
  body = await page.locator('body').innerText();
  assert(body.includes('44%'), 'root aggregate did not return to 44% after moving the acceptance child');

  consoleMessages.length = 0;
  await page.getByRole('button', { name: '项目树', exact: true }).click();
  await page.getByRole('button', { name: '子树移动', exact: true }).click();
  const rejectDialog = page.locator('.el-dialog').filter({ hasText: '目标父项目' });
  await rejectDialog.getByText('请选择目标父项目', { exact: true }).click();
  await rejectDialog.locator('.el-select__input').fill('上海办事处交付');
  await page.getByText('上海办事处交付', { exact: true }).last().click();
  const rejectResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/admin-api/pms/projects/920001/actions/move') && response.request().method() === 'POST');
  await rejectDialog.getByRole('button', { name: '保存', exact: true }).click();
  const rejectPayload = await (await rejectResponsePromise).json();
  assert(rejectPayload.code !== 0, 'cycle move unexpectedly succeeded');
  await page.waitForTimeout(500);
  assert(!consoleMessages.some((item) => item.text.includes('Unhandled error during execution of component event handler')),
    'cycle rejection still causes an unhandled Vue event warning');
  assert(failedRequests.length === 0, `failed requests: ${JSON.stringify(failedRequests)}`);
  assert(errorResponses.length === 0, `HTTP errors: ${JSON.stringify(errorResponses)}`);

  const evidence = {
    steps: [
      { name: 'manual-weight-full-replacement', status: 'PASS', weights: { acceptance: 20, shanghai: 50, jiangsu: 30 }, aggregate: '34%' },
      { name: 'move-restores-seed-aggregate', status: 'PASS', aggregate: '44%' },
      { name: 'cycle-rejection-no-unhandled-warning', status: 'PASS', businessCode: rejectPayload.code }
    ],
    consoleMessages,
    failedRequests,
    errorResponses
  };
  fs.writeFileSync(path.join(evidenceDir, 't8-weight-evidence.json'), JSON.stringify(evidence, null, 2));
  console.log(JSON.stringify(evidence, null, 2));
  await browser.close();
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
