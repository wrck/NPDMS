const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

(async () => {
  const evidenceDir = path.join(__dirname, 'fpm02-t8');
  fs.mkdirSync(evidenceDir, { recursive: true });
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
  page.on('requestfailed', (request) => {
    failedRequests.push({ url: request.url(), failure: request.failure()?.errorText });
  });
  page.on('response', (response) => {
    if (response.status() >= 400) {
      errorResponses.push({ url: response.url(), status: response.status() });
    }
  });

  await page.goto('http://localhost:18081', { waitUntil: 'networkidle' });
  await page.screenshot({ path: path.join(evidenceDir, '01-entry.png'), fullPage: true });
  const evidence = {
    url: page.url(),
    title: await page.title(),
    bodyText: (await page.locator('body').innerText()).slice(0, 4000),
    inputs: await page.locator('input').evaluateAll((nodes) => nodes.map((node) => ({
      type: node.type,
      placeholder: node.placeholder,
      name: node.name
    }))),
    buttons: await page.getByRole('button').allTextContents(),
    consoleMessages,
    failedRequests,
    errorResponses
  };
  fs.writeFileSync(path.join(evidenceDir, '01-entry.json'), JSON.stringify(evidence, null, 2));
  console.log(JSON.stringify(evidence, null, 2));
  await browser.close();
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
