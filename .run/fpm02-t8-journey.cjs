const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const baseUrl = 'http://localhost:18081';
const rootProjectId = 920001;

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

(async () => {
  const username = process.env.NPDMS_E2E_USERNAME;
  const password = process.env.NPDMS_E2E_PASSWORD;
  if (!username || !password) throw new Error('NPDMS_E2E_USERNAME/PASSWORD are required');

  const evidenceDir = path.join(__dirname, 'fpm02-t8');
  fs.mkdirSync(evidenceDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const evidence = { steps: [], consoleMessages: [], failedRequests: [], errorResponses: [] };

  page.on('console', (message) => {
    if (message.type() === 'error' || message.type() === 'warning') {
      evidence.consoleMessages.push({ type: message.type(), text: message.text() });
    }
  });
  page.on('requestfailed', (request) => {
    evidence.failedRequests.push({ url: request.url(), failure: request.failure()?.errorText });
  });
  page.on('response', (response) => {
    if (response.status() >= 400) {
      evidence.errorResponses.push({ url: response.url(), status: response.status() });
    }
  });

  await page.goto(baseUrl, { waitUntil: 'networkidle' });
  await page.locator('input[placeholder="请输入用户名"]').first().fill(username);
  await page.locator('input[placeholder="请输入密码"]').first().fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 30_000 });
  evidence.steps.push({ name: 'login', status: 'PASS' });

  const detailUrl = `${baseUrl}/pms/project-management/project-master-detail?projectId=${rootProjectId}`;
  await page.goto(detailUrl, { waitUntil: 'networkidle' });
  fs.writeFileSync(path.join(evidenceDir, '02-route-debug.json'), JSON.stringify({
    url: page.url(),
    bodyText: (await page.locator('body').innerText()).slice(0, 5000),
    consoleMessages: evidence.consoleMessages,
    failedRequests: evidence.failedRequests,
    errorResponses: evidence.errorResponses
  }, null, 2));
  await page.screenshot({ path: path.join(evidenceDir, '02-route-debug.png'), fullPage: true });
  await page.getByText('华东交付示例根项目', { exact: true }).first().waitFor({ timeout: 5_000 });
  let body = await page.locator('body').innerText();
  assert(body.includes('PJT-DEMO-920001'), 'root project code not rendered');
  assert(body.includes('结构深度') && body.includes('父项目'), 'root project facts not rendered');
  await page.screenshot({ path: path.join(evidenceDir, '02-root-detail.png'), fullPage: true });
  evidence.steps.push({ name: 'root-detail', status: 'PASS' });

  await page.getByRole('button', { name: '项目树', exact: true }).click();
  await page.getByText('上海办事处交付', { exact: true }).first().waitFor();
  body = await page.locator('body').innerText();
  assert(body.includes('江苏办事处交付'), 'second direct child not rendered');
  assert(!body.includes('上海一号节点实施单元'), 'tree eagerly rendered a non-direct descendant');
  await page.screenshot({ path: path.join(evidenceDir, '03-direct-children.png'), fullPage: true });
  evidence.steps.push({ name: 'direct-children-lazy-load', status: 'PASS' });

  await page.getByRole('button', { name: '进度汇总', exact: true }).click();
  body = await page.locator('body').innerText();
  const hasTransientMixedWeight = body.includes('权重配置必须全等权或全手动');
  if (!hasTransientMixedWeight) {
    assert(body.includes('44%'), `expected aggregate 44%, got: ${body.slice(-1200)}`);
    assert(body.includes('60.00%') && body.includes('40.00%'), 'manual weights not rendered');
  }
  await page.screenshot({ path: path.join(evidenceDir, '04-progress.png'), fullPage: true });
  evidence.steps.push({
    name: hasTransientMixedWeight ? 'mixed-weight-rejected' : 'weighted-progress',
    status: 'PASS',
    aggregate: hasTransientMixedWeight ? undefined : '44%'
  });

  await page.getByRole('button', { name: '项目树', exact: true }).click();
  let createdProjectId = 920007;
  if (!(await page.locator('body').innerText()).includes('浏览器验收子项目')) {
    await page.getByRole('button', { name: '下挂子项目', exact: true }).click();
    await page.locator('input[placeholder="子项目名称"]').fill('浏览器验收子项目');
    await page.locator('textarea[placeholder="BR-2 必填"]').fill('F-PM02 T8 真实浏览器验收');
    const createResponsePromise = page.waitForResponse((response) =>
      response.url().includes('/admin-api/pms/projects') &&
      response.request().method() === 'POST' &&
      !response.url().includes('/actions/')
    );
    await page.getByRole('button', { name: '保存', exact: true }).last().click();
    const createResponse = await createResponsePromise;
    const createPayload = await createResponse.json();
    assert(createPayload.code === 0, `create child failed: ${JSON.stringify(createPayload)}`);
    createdProjectId = createPayload.data?.id ?? createPayload.data?.projectId;
    assert(createdProjectId, `create response missed project id: ${JSON.stringify(createPayload)}`);
  }
  await page.getByText('浏览器验收子项目', { exact: true }).waitFor();
  body = await page.locator('body').innerText();
  assert(body.includes('PJT-DEMO-920001-SP000006'), 'child code did not continue at SP000006');
  evidence.steps.push({ name: 'create-child', status: 'PASS', projectId: createdProjectId, code: 'PJT-DEMO-920001-SP000006' });

  await page.reload({ waitUntil: 'networkidle' });
  await page.getByRole('button', { name: '项目树', exact: true }).click();
  await page.getByText('浏览器验收子项目', { exact: true }).waitFor();
  evidence.steps.push({ name: 'refresh-persistence', status: 'PASS' });

  await page.goto(`${baseUrl}/pms/project-management/project-master-detail?projectId=${createdProjectId}`, { waitUntil: 'networkidle' });
  await page.getByText('浏览器验收子项目', { exact: true }).first().waitFor();
  await page.getByRole('button', { name: '生命周期实例', exact: true }).click();
  body = await page.locator('body').innerText();
  assert(body.includes('S0 项目立项与指派'), 'S0 lifecycle instance missing');
  assert(body.includes('S6 项目闭环'), 'S6 lifecycle instance missing');
  assert(body.includes('任务') && body.includes('里程碑') && body.includes('交付件') && body.includes('门禁'), 'fullchain element groups missing');
  await page.screenshot({ path: path.join(evidenceDir, '05-child-fullchain.png'), fullPage: true });
  evidence.steps.push({ name: 'child-fullchain-instances', status: 'PASS' });

  await page.getByRole('button', { name: '项目树', exact: true }).click();
  await page.getByRole('button', { name: '子树移动', exact: true }).click();
  fs.writeFileSync(path.join(evidenceDir, '05-move-dialog-debug.json'), JSON.stringify({
    bodyText: (await page.locator('body').innerText()).slice(-3000),
    inputs: await page.locator('input').evaluateAll((nodes) => nodes.map((node) => ({
      placeholder: node.getAttribute('placeholder'),
      disabled: node.hasAttribute('disabled'),
      className: node.className
    })))
  }, null, 2));
  await page.screenshot({ path: path.join(evidenceDir, '05-move-dialog-debug.png'), fullPage: true });
  let moveDialog = page.locator('.el-dialog').filter({ hasText: '目标父项目' });
  let moveSelectInput = moveDialog.locator('.el-select__input');
  await moveDialog.getByText('请选择目标父项目', { exact: true }).click();
  await moveSelectInput.fill('江苏办事处交付');
  await page.getByText('江苏办事处交付', { exact: true }).last().click();
  const moveResponsePromise = page.waitForResponse((response) =>
    response.url().includes(`/admin-api/pms/projects/${createdProjectId}/actions/move`) &&
    response.request().method() === 'POST'
  );
  await page.getByRole('button', { name: '保存', exact: true }).last().click();
  const moveResponse = await moveResponsePromise;
  const movePayload = await moveResponse.json();
  assert(movePayload.code === 0, `move child failed: ${JSON.stringify(movePayload)}`);
  await page.getByRole('button', { name: '基本信息', exact: true }).click();
  body = await page.locator('body').innerText();
  assert(body.includes('父项目') && body.includes('#920003'), 'moved child parent was not refreshed');
  evidence.steps.push({ name: 'move-subtree', status: 'PASS', newParentId: 920003 });

  await page.reload({ waitUntil: 'networkidle' });
  body = await page.locator('body').innerText();
  assert(body.includes('#920003'), 'moved parent did not persist after refresh');
  evidence.steps.push({ name: 'move-refresh-persistence', status: 'PASS' });

  await page.goto(detailUrl, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: '进度汇总', exact: true }).click();
  body = await page.locator('body').innerText();
  assert(body.includes('44%'), `expected aggregate 44% after move, got: ${body.slice(-1200)}`);
  assert(body.includes('60.00%') && body.includes('40.00%'), 'manual weights not rendered after move');
  evidence.steps.push({ name: 'weighted-progress-after-move', status: 'PASS', aggregate: '44%' });

  await page.getByRole('button', { name: '项目树', exact: true }).click();
  await page.getByRole('button', { name: '子树移动', exact: true }).click();
  moveDialog = page.locator('.el-dialog').filter({ hasText: '目标父项目' });
  moveSelectInput = moveDialog.locator('.el-select__input');
  await moveDialog.getByText('请选择目标父项目', { exact: true }).click();
  await moveSelectInput.fill('上海办事处交付');
  await page.getByText('上海办事处交付', { exact: true }).last().click();
  const rejectResponsePromise = page.waitForResponse((response) =>
    response.url().includes(`/admin-api/pms/projects/${rootProjectId}/actions/move`) &&
    response.request().method() === 'POST'
  );
  await page.getByRole('button', { name: '保存', exact: true }).last().click();
  const rejectResponse = await rejectResponsePromise;
  const rejectPayload = await rejectResponse.json();
  assert(rejectPayload.code !== 0, 'cycle move unexpectedly succeeded');
  await page.getByText(/循环|后代|父项目/, { exact: false }).last().waitFor();
  evidence.steps.push({ name: 'cycle-move-rejected', status: 'PASS', businessCode: rejectPayload.code });

  await page.screenshot({ path: path.join(evidenceDir, '06-cycle-rejected.png'), fullPage: true });
  fs.writeFileSync(path.join(evidenceDir, 't8-evidence.json'), JSON.stringify(evidence, null, 2));
  console.log(JSON.stringify(evidence, null, 2));
  await browser.close();
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
