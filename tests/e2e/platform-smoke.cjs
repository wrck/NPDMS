const { chromium } = require("playwright");
const fs = require("fs");

const baseUrl = process.env.NPDMS_E2E_BASE_URL || "http://localhost:18081";
const username = process.env.NPDMS_E2E_USERNAME;
const password = process.env.NPDMS_E2E_PASSWORD;
const executablePath = process.env.NPDMS_CHROME_PATH;

if (!username || !password || !executablePath) {
  throw new Error("NPDMS_E2E_USERNAME, NPDMS_E2E_PASSWORD and NPDMS_CHROME_PATH are required");
}

(async () => {
  const browser = await chromium.launch({ executablePath, headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const consoleErrors = [];
  const businessErrors = [];

  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  page.on("response", async (response) => {
    if (!response.url().includes("/admin-api/")) return;
    const contentType = response.headers()["content-type"] || "";
    if (!contentType.includes("application/json")) return;
    try {
      const body = await response.json();
      if (typeof body?.code === "number" && body.code !== 0) {
        businessErrors.push({ url: response.url(), code: body.code, msg: body.msg });
      }
    } catch {
      // Non-JSON bodies are irrelevant to the business-code assertion.
    }
  });

  await page.goto(baseUrl, { waitUntil: "networkidle" });
  const userInput = page.locator('input[placeholder*="账号"], input[placeholder*="用户名"]').first();
  const passwordInput = page.locator('input[type="password"]').first();
  await userInput.fill(username);
  await passwordInput.fill(password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 30_000 });
  await page.waitForLoadState("networkidle");

  const bodyText = await page.locator("body").innerText();
  if (bodyText.includes("请求的租户标识未传递")) {
    throw new Error("tenant identifier error is still visible after login");
  }

  for (const menuName of ["系统管理", "基础设施", "工作流程"]) {
    const menu = page.getByText(menuName, { exact: true }).first();
    if ((await menu.count()) === 0) throw new Error(`missing menu: ${menuName}`);
    await menu.click();
    await page.waitForTimeout(500);
  }

  fs.mkdirSync(".run", { recursive: true });
  await page.screenshot({ path: ".run/platform-smoke.png", fullPage: true });
  if (consoleErrors.length) {
    throw new Error(`console errors: ${JSON.stringify(consoleErrors)}`);
  }
  if (businessErrors.length) {
    throw new Error(`business API errors: ${JSON.stringify(businessErrors)}`);
  }

  console.log("platform browser smoke passed");
  await browser.close();
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
