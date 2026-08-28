const fs = require("node:fs");
const path = require("node:path");

const baseUrl = process.env.NPDMS_BROWSER_BASE_URL || "http://127.0.0.1:19081";
const apiUrl =
  process.env.NPDMS_BROWSER_API_URL || "http://localhost:59280/admin-api";
const crossTenantApiUrl =
  process.env.NPDMS_CROSS_TENANT_API_URL || "http://127.0.0.1:59282/admin-api";
const executablePath = process.env.NPDMS_BROWSER_EXECUTABLE || undefined;
const managerUsername = process.env.NPDMS_BROWSER_USERNAME || "admin";
const managerPassword = process.env.NPDMS_BROWSER_PASSWORD || "admin123";
const managerTenantName =
  process.env.NPDMS_BROWSER_TENANT_NAME || "NPMS默认租户";
const managerTenantId = process.env.NPDMS_BROWSER_TENANT_ID || "1";
const minioAccessKey = process.env.NPDMS_MINIO_ACCESS_KEY;
const minioAccessSecret = process.env.NPDMS_MINIO_ACCESS_SECRET;
const expectedScan = process.env.NPDMS_EXPECTED_SCAN || "SKIPPED";
const negativePassword =
  process.env.NPDMS_FSOL003_NEGATIVE_PASSWORD || "Fsol003!Browser1";
const projectId = Number(process.env.NPDMS_FSOL003_PROJECT_ID || 992203060001);
let projectTemplateId;
const compatibleTemplateId = Number(
  process.env.NPDMS_FSOL003_DYNAMIC_TEMPLATE_ID || 992203010001,
);
const compatibleRevisionId = Number(
  process.env.NPDMS_FSOL003_DYNAMIC_REVISION_ID || 992203020001,
);
const incompatibleRevisionId = Number(
  process.env.NPDMS_FSOL003_INCOMPATIBLE_REVISION_ID || 992203020002,
);
const incompatibleTemplateId = Number(
  process.env.NPDMS_FSOL003_INCOMPATIBLE_TEMPLATE_ID || 992203010002,
);
const outputDir = path.resolve(
  process.argv[2] || "docs/engineering/evidence/f-sol-003-dynamic-form",
);
const projectPath = `/pms/project-management/project-master-detail?projectId=${projectId}&tab=requirement-analysis`;

fs.mkdirSync(outputDir, { recursive: true });

const requiredInputs = {
  NPDMS_FSOL003_PROJECT_ID: Number.isSafeInteger(projectId) && projectId > 0,
  NPDMS_MINIO_ACCESS_KEY: Boolean(minioAccessKey),
  NPDMS_MINIO_ACCESS_SECRET: Boolean(minioAccessSecret),
  NPDMS_EXPECTED_SCAN: ["PASSED", "SKIPPED"].includes(expectedScan),
};
const runSlug = expectedScan.toLowerCase();

const assert = (condition, message) => {
  if (!condition) throw new Error(message);
};

const loadPlaywright = () => {
  try {
    return require("playwright");
  } catch (error) {
    if (process.env.NPDMS_PLAYWRIGHT_MODULE) {
      return require(path.resolve(process.env.NPDMS_PLAYWRIGHT_MODULE));
    }
    throw new Error(
      `无法加载Playwright；请设置NODE_PATH为Codex工作区Node packages，或设置NPDMS_PLAYWRIGHT_MODULE为playwright模块目录。原错误：${error.message}`,
    );
  }
};

const nowKey = (scope) =>
  `fsol003-browser-${scope}-${Date.now()}-${crypto.randomUUID()}`;

(async () => {
  const missingInputs = Object.entries(requiredInputs)
    .filter(([, present]) => !present)
    .map(([name]) => name);
  if (missingInputs.length) {
    const failure = {
      capturedAt: new Date().toISOString(),
      status: "NOT_RUN",
      blocker: "MISSING_ACCEPTANCE_FIXTURE_INPUT",
      missingInputs,
      note: "项目模板验收草稿、负向用户/角色、项目授权和第二租户令牌均由脚本通过公开REST自建/回收；仅依赖V105固定manager_id=1浏览器项目。",
    };
    fs.writeFileSync(
      path.join(outputDir, `browser-run-${runSlug}-not-run.json`),
      `${JSON.stringify(failure, null, 2)}\n`,
    );
    throw new Error(`F-SOL-003浏览器验收缺少输入：${missingInputs.join(", ")}`);
  }

  const { chromium } = loadPlaywright();
  const browser = await chromium.launch({ headless: true, executablePath });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
  });
  const page = await context.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  const requestFailures = [];
  const unexpectedResponses = [];
  const relevantHttp = [];
  const expectedNegativeResponses = [];
  const expectedDiagnostics = [];
  const expectedFailurePaths = new Set();
  const screenshots = [];
  const assertions = [];
  const cleanup = {
    grants: [],
    users: [],
    roles: [],
    templates: [],
    tenants: [],
    tenantPackages: [],
  };
  let readonlyActor;
  let nonManagerActor;
  let secondTenant;

  const recordPage = (targetPage, actor) => {
    targetPage.on("console", (message) => {
      if (message.type() === "error")
        consoleErrors.push({ actor, message: message.text() });
    });
    targetPage.on("pageerror", (error) =>
      pageErrors.push({ actor, message: error.message }),
    );
    targetPage.on("requestfailed", (request) =>
      requestFailures.push({
        actor,
        method: request.method(),
        url: request.url(),
        error: request.failure()?.errorText,
      }),
    );
    targetPage.on("response", (response) => {
      const url = response.url();
      if (!url.includes("/admin-api/")) return;
      const item = {
        actor,
        method: response.request().method(),
        status: response.status(),
        path: new URL(url).pathname,
      };
      if (
        /preparations|dynamic-form|project-templates|files|file-references/.test(
          url,
        )
      ) {
        relevantHttp.push(item);
      }
      if (expectedFailurePaths.has(item.path)) {
        expectedNegativeResponses.push(item);
      } else if (response.status() >= 400) {
        unexpectedResponses.push(item);
      }
    });
  };
  recordPage(page, "MANAGER_TEMPLATE_ADMIN");

  const waitForAbortedRequestDiagnostics = (method, urlFragment) =>
    Promise.all([
      page.waitForEvent("requestfailed", (request) =>
        Boolean(
          request.method() === method &&
          request.url().includes(urlFragment) &&
          request.failure()?.errorText.includes("ERR_FAILED"),
        ),
      ),
      page.waitForEvent(
        "console",
        (message) =>
          message.type() === "error" && message.text().includes("ERR_FAILED"),
      ),
    ]);

  const login = async (targetPage, username, password) => {
    await targetPage.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
    const textInputs = targetPage.locator('input[type="text"]:visible');
    if ((await textInputs.count()) > 1) {
      await textInputs.first().fill(managerTenantName);
    }
    await textInputs.last().fill(username);
    await targetPage
      .locator('input[type="password"]:visible')
      .first()
      .fill(password);
    await targetPage.getByRole("button", { name: "登录", exact: true }).click();
    await targetPage.waitForURL((url) => !url.pathname.startsWith("/login"));
  };

  const tokenOf = async (targetPage) =>
    targetPage.evaluate(() => {
      const cached = localStorage.getItem("ACCESS_TOKEN");
      try {
        const item = JSON.parse(cached);
        return item?.v ? JSON.parse(item.v) : item;
      } catch {
        return cached;
      }
    });

  const api = async (
    targetPage,
    input,
    targetApiUrl = apiUrl,
    tokenOverride,
  ) => {
    const token = tokenOverride || (await tokenOf(targetPage));
    const response = await context.request.fetch(
      `${targetApiUrl}${input.path}`,
      {
        method: input.method || "GET",
        headers: {
          Authorization: `Bearer ${token}`,
          "tenant-id": managerTenantId,
          ...(input.body ? { "Content-Type": "application/json" } : {}),
          ...(input.headers || {}),
        },
        data: input.body,
      },
    );
    const body = await response.json().catch(() => undefined);
    const item = {
      actor: "PUBLIC_REST",
      method: input.method || "GET",
      status: response.status(),
      path: new URL(`${targetApiUrl}${input.path}`).pathname,
    };
    if (
      /preparations|dynamic-form|project-templates|files|file-references/.test(
        item.path,
      )
    ) {
      relevantHttp.push(item);
    }
    if (response.status() >= 400 || body?.code !== 0) {
      if (expectedFailurePaths.has(item.path))
        expectedNegativeResponses.push(item);
      else unexpectedResponses.push(item);
    }
    return { status: response.status(), body };
  };

  const expectSuccess = (result, label) => {
    assert(
      result.status === 200 && result.body?.code === 0,
      `${label}失败：${JSON.stringify(result)}`,
    );
    return result.body.data;
  };

  const createBrowserActor = async (actorCode, menuIds) => {
    const suffix = `${Date.now()}`.slice(-9);
    const roleId = expectSuccess(
      await api(page, {
        path: "/system/role/create",
        method: "POST",
        body: {
          name: `FSOL003-${actorCode}-${suffix}`,
          code: `fsol003_${actorCode}_${suffix}`,
          sort: 90,
          status: 0,
        },
      }),
      `创建${actorCode}角色`,
    );
    cleanup.roles.push(roleId);
    expectSuccess(
      await api(page, {
        path: "/system/permission/assign-role-menu",
        method: "POST",
        body: { roleId, menuIds },
      }),
      `分配${actorCode}菜单`,
    );
    const username = `fs${suffix}${actorCode.slice(0, 2)}`;
    const userId = expectSuccess(
      await api(page, {
        path: "/system/user/create",
        method: "POST",
        body: {
          username,
          nickname: `FSOL003 ${actorCode}`,
          password: negativePassword,
          status: 0,
          sex: 1,
          postIds: [],
        },
      }),
      `创建${actorCode}用户`,
    );
    cleanup.users.push(userId);
    expectSuccess(
      await api(page, {
        path: "/system/permission/assign-user-role",
        method: "POST",
        body: { userId, roleIds: [roleId] },
      }),
      `分配${actorCode}角色`,
    );
    const grant = expectSuccess(
      await api(page, {
        path: `/pms/projects/${projectId}/authorization-grants`,
        method: "POST",
        headers: { "Idempotency-Key": nowKey(`${actorCode}-project-view`) },
        body: {
          subjectUserId: userId,
          actionCode: "PROJECT_VIEW",
          scopeCode: "CURRENT_PROJECT",
          reason: "F-SOL-003真实浏览器隔离验收",
        },
      }),
      `授予${actorCode}项目查看权限`,
    );
    cleanup.grants.push({ id: grant.id, version: grant.version });
    return { username, password: negativePassword, userId, roleId };
  };

  const cleanupActors = async () => {
    for (const tenantId of cleanup.tenants.reverse()) {
      await api(page, {
        path: `/system/tenant/delete?id=${tenantId}`,
        method: "DELETE",
      }).catch(() => undefined);
    }
    for (const packageId of cleanup.tenantPackages.reverse()) {
      await api(page, {
        path: `/system/tenant-package/delete?id=${packageId}`,
        method: "DELETE",
      }).catch(() => undefined);
    }
    for (const template of cleanup.templates.reverse()) {
      await api(page, {
        path: template.published
          ? `/pms/project-templates/${template.id}/actions/disable`
          : `/pms/project-templates/${template.id}`,
        method: template.published ? "POST" : "DELETE",
      }).catch(() => undefined);
    }
    for (const grant of cleanup.grants.reverse()) {
      await api(page, {
        path: `/pms/project-authorization-grants/${grant.id}/actions/revoke`,
        method: "POST",
        headers: {
          "Idempotency-Key": nowKey("cleanup-grant"),
          "If-Match": String(grant.version),
        },
        body: { reason: "F-SOL-003浏览器验收清理" },
      }).catch(() => undefined);
    }
    for (const userId of cleanup.users.reverse()) {
      await api(page, {
        path: `/system/user/delete?id=${userId}`,
        method: "DELETE",
      }).catch(() => undefined);
    }
    for (const roleId of cleanup.roles.reverse()) {
      await api(page, {
        path: `/system/role/delete?id=${roleId}`,
        method: "DELETE",
      }).catch(() => undefined);
    }
  };

  const screenshot = async (name, targetPage = page) => {
    const target = path.join(outputDir, `${runSlug}-${name}`);
    await targetPage.screenshot({ path: target, fullPage: true });
    screenshots.push(
      path.relative(process.cwd(), target).replaceAll("\\", "/"),
    );
  };

  const confirmMessageBox = async (targetPage = page) => {
    const box = targetPage.locator(".el-message-box:visible");
    await box.waitFor();
    await box.getByRole("button", { name: "确定", exact: true }).click();
  };

  const cancelMessageBox = async (targetPage = page) => {
    const box = targetPage.locator(".el-message-box:visible");
    await box.waitFor();
    await box.getByRole("button", { name: "取消", exact: true }).click();
  };

  const closeVisibleDrawer = async (title, targetPage = page) => {
    const drawer = targetPage
      .locator(".el-drawer:visible")
      .filter({ hasText: title });
    await drawer.waitFor();
    assert(
      (await drawer.count()) === 1,
      `当前可见抽屉无法按标题唯一定位：${title}`,
    );
    await drawer
      .getByRole("button", { name: "关闭此对话框", exact: true })
      .click();
  };

  const openRequirementWorkspace = async (targetPage = page) => {
    await targetPage.goto(`${baseUrl}${projectPath}`, {
      waitUntil: "networkidle",
    });
    const rail = targetPage.getByRole("button", {
      name: "需求分析",
      exact: true,
    });
    if (await rail.isVisible()) await rail.click();
    await targetPage
      .getByRole("heading", { name: "需求分析", exact: true })
      .waitFor();
  };

  const getWorkspace = (targetPage = page) =>
    api(targetPage, {
      path: `/api/v1/pms/preparations?projectId=${projectId}&type=PRE_04`,
    });

  const getDetail = (preparationId, targetPage = page) =>
    api(targetPage, {
      path: `/api/v1/pms/preparations/${preparationId}?type=PRE_04`,
    });
  const controlledFacts = (detail, fieldKey) =>
    detail.controlledFiles?.[`FORM_FIELD_ATTACHMENT/${fieldKey}`] ||
    detail.controlledFiles?.[fieldKey] ||
    [];

  const fillRichText = async (label, value, targetPage = page) => {
    const item = targetPage
      .locator(".el-form-item")
      .filter({ hasText: label })
      .first();
    const editor = item.locator('[contenteditable="true"]').last();
    await editor.fill(value);
    await editor.blur();
  };

  const saveForm = async (targetPage = page) => {
    await targetPage
      .getByRole("button", { name: "保存表单", exact: true })
      .click();
    await targetPage
      .getByText(/需求分析表单已保存|已确认需求分析表单保存成功/)
      .waitFor();
  };

  const uploadControlledFile = async (
    name,
    content,
    targetPage = page,
    replace = false,
  ) => {
    const inputFile = {
      name,
      mimeType: "text/plain",
      buffer: Buffer.from(content),
    };
    let initializedResponse;
    let completed;
    for (let attempt = 0; attempt < 10; attempt += 1) {
      const currentField = targetPage
        .locator(".el-form-item")
        .filter({ hasText: "项目背景附件" })
        .first();
      const currentUploader = replace
        ? currentField
            .locator(".pms-file-uploader")
            .filter({ hasText: "上传新版本" })
            .first()
        : currentField.locator(".pms-file-uploader").last();
      await currentUploader
        .locator('input[type="file"]')
        .setInputFiles(inputFile);
      const currentButton = currentUploader.getByRole("button", {
        name: replace ? "上传新版本" : "上传并绑定",
        exact: true,
      });
      const initialized = targetPage
        .waitForResponse(
          (response) =>
            response.request().method() === "POST" &&
            response.url().includes("files:init-upload"),
          { timeout: 5000 },
        )
        .catch(() => undefined);
      const completion = targetPage
        .waitForResponse(
          (response) =>
            response.request().method() === "POST" &&
            response.url().includes(":complete-upload"),
          { timeout: 30000 },
        )
        .catch(() => undefined);
      try {
        await currentButton.click({ timeout: 5000 });
      } catch (error) {
        if (/not attached|not stable|Timeout/i.test(String(error))) {
          await initialized;
          continue;
        }
        throw error;
      }
      initializedResponse = await initialized;
      if (!initializedResponse) continue;
      completed = await completion;
      break;
    }
    assert(initializedResponse, `${name}未通过当前上传器发出初始化请求`);
    const initializedBody = await initializedResponse
      .json()
      .catch(() => undefined);
    assert(
      initializedResponse.status() === 200 && initializedBody?.code === 0,
      `受控文件初始化失败：${name} ${JSON.stringify(initializedBody)}`,
    );
    assert(completed, `${name}未完成上传请求`);
    const completedBody = await completed.json().catch(() => undefined);
    assert(
      completed.status() === 200 && completedBody?.code === 0,
      `受控文件完成失败：${name} ${JSON.stringify(completedBody)}`,
    );
    if (!replace) {
      await targetPage
        .getByText(name, { exact: true })
        .waitFor({ timeout: 30000 });
    }
    return completedBody.data;
  };

  const uploadControlledFileWithUnknownResponse = async (name, content) => {
    const currentUploader = () =>
      page
        .locator(".el-form-item")
        .filter({ hasText: "项目背景附件" })
        .first()
        .locator(".pms-file-uploader")
        .last();
    const currentSubmit = () =>
      currentUploader().getByRole("button", {
        name: "上传并绑定",
        exact: true,
      });
    const observed = { init: [], complete: [] };
    const observe = (request) => {
      if (request.method() !== "POST") return;
      if (request.url().includes("files:init-upload")) {
        observed.init.push({
          referenceKey: request.postDataJSON()?.referenceKey,
          key: request.headers()["idempotency-key"],
        });
      }
      if (request.url().includes(":complete-upload")) {
        observed.complete.push({
          url: request.url(),
          key: request.headers()["idempotency-key"],
        });
      }
    };
    page.on("request", observe);
    let injected = false;
    await page.route("**/*:complete-upload", async (route) => {
      if (!injected) {
        injected = true;
        await route.fetch();
        await route.abort();
        return;
      }
      await route.continue();
    });
    try {
      const abortDiagnostics = waitForAbortedRequestDiagnostics(
        "POST",
        ":complete-upload",
      );
      await currentUploader()
        .locator('input[type="file"]')
        .setInputFiles({
          name,
          mimeType: "text/plain",
          buffer: Buffer.from(content),
        });
      await currentSubmit().click();
      await currentSubmit().waitFor({ state: "visible" });
      await currentSubmit().click({ trial: true, timeout: 5000 });
      const completed = page.waitForResponse(
        (response) =>
          response.request().method() === "POST" &&
          response.url().includes(":complete-upload") &&
          response.status() === 200,
      );
      await currentSubmit().click();
      await completed;
      await page.getByText(name, { exact: true }).waitFor({ timeout: 30000 });
      await abortDiagnostics;
      await page.waitForLoadState("networkidle");
    } finally {
      page.off("request", observe);
      await page.unroute("**/*:complete-upload");
    }
    assert(
      injected &&
        observed.init.length === 1 &&
        observed.complete.length === 2 &&
        observed.init[0].referenceKey &&
        observed.complete[0].url === observed.complete[1].url &&
        observed.complete[0].key === observed.complete[1].key,
      "响应未知重试未沿用原slot、Artifact与Idempotency-Key",
    );
    return observed;
  };

  const setBindingRevision = (content, templateId, revisionId) => {
    const cloned = structuredClone(content);
    const task = cloned.tasks?.find(
      (item) =>
        item.taskDefinitionKey === "T-REQ-ANALYSIS" ||
        item.taskCode === "T-REQ-ANALYSIS",
    );
    assert(task, "项目模板草稿缺少T-REQ-ANALYSIS任务，不能验证WorkBinding发布");
    task.workBindingTypeCode = "BUSINESS_OBJECT";
    task.targetContextCode = "SOL";
    task.targetObjectType = "REQUIREMENT_ANALYSIS";
    task.targetObjectKey = "PRE_04_REQUIREMENT_ANALYSIS";
    task.dynamicFormRevisionId = null;
    task.bindingConfig = JSON.stringify({
      schemaVersion: 2,
      dynamicFormTemplateId: templateId,
      dynamicFormTemplateRevisionId: revisionId,
      dynamicFormRevisionNo: 1,
      dynamicFormRevisionFactVersion: 1,
    });
    return cloned;
  };

  try {
    await login(page, managerUsername, managerPassword);

    const fileConfigPage = expectSuccess(
      await api(page, {
        path: "/infra/file-config/page?pageNo=1&pageSize=100",
      }),
      "文件配置分页读取",
    );
    let minioConfig = fileConfigPage.list?.find(
      (item) => item.name === "F-SOL-003 浏览器 MinIO",
    );
    if (!minioConfig) {
      const minioConfigId = expectSuccess(
        await api(page, {
          path: "/infra/file-config/create",
          method: "POST",
          body: {
            name: "F-SOL-003 浏览器 MinIO",
            storage: 20,
            remark: "F-SOL-003隔离浏览器验收",
            config: {
              endpoint: "http://127.0.0.1:9000",
              bucket: "yudao",
              accessKey: minioAccessKey,
              accessSecret: minioAccessSecret,
              enablePathStyleAccess: true,
              enablePublicAccess: false,
              region: "us-east-1",
              domain: "http://127.0.0.1:9000/yudao",
            },
          },
        }),
        "创建MinIO文件配置",
      );
      minioConfig = { id: minioConfigId };
    }
    expectSuccess(
      await api(page, {
        path: `/infra/file-config/update-master?id=${minioConfig.id}`,
        method: "PUT",
      }),
      "切换MinIO主配置",
    );
    expectSuccess(
      await api(page, {
        path: `/infra/file-config/test?id=${minioConfig.id}`,
      }),
      "MinIO配置探针",
    );

    const projectWorkspaceMenus = [18000, 19261, 18067, 18071, 198780, 198794];
    readonlyActor = await createBrowserActor("readonly", projectWorkspaceMenus);
    nonManagerActor = await createBrowserActor("nonmanager", [
      ...projectWorkspaceMenus,
      198795,
    ]);
    const tenantSuffix = `${Date.now()}`.slice(-9);
    const tenantPackageId = expectSuccess(
      await api(page, {
        path: "/system/tenant-package/create",
        method: "POST",
        body: {
          name: `FSOL003-BROWSER-${tenantSuffix}`,
          status: 0,
          remark: "F-SOL-003跨租户浏览器验收临时套餐",
          menuIds: [],
        },
      }),
      "创建第二租户临时套餐",
    );
    cleanup.tenantPackages.push(tenantPackageId);
    const secondTenantUsername = `fs${tenantSuffix}tenant`;
    const secondTenantId = expectSuccess(
      await api(page, {
        path: "/system/tenant/create",
        method: "POST",
        body: {
          name: `FSOL003浏览器租户${tenantSuffix}`,
          contactName: "F-SOL-003浏览器验收",
          status: 0,
          websites: [],
          packageId: tenantPackageId,
          expireTime: Date.UTC(2099, 11, 31, 23, 59, 59),
          accountCount: 5,
          username: secondTenantUsername,
          password: negativePassword,
        },
      }),
      "创建第二租户及登录用户",
    );
    cleanup.tenants.push(secondTenantId);
    secondTenant = {
      id: secondTenantId,
      username: secondTenantUsername,
      password: negativePassword,
    };

    const fixedProject = expectSuccess(
      await api(page, { path: `/pms/projects/${projectId}` }),
      "V105固定浏览器项目读取",
    );
    assert(
      fixedProject.lifecycleStatus === "ACTIVE" &&
        fixedProject.currentStage === "S1",
      "V105固定浏览器项目不是ACTIVE/S1项目",
    );
    const fixedProjectMembers = expectSuccess(
      await api(page, { path: `/pms/projects/${projectId}/members` }),
      "V105固定浏览器项目成员读取",
    );
    assert(
      fixedProjectMembers.some(
        (item) =>
          item.userId === 1 &&
          item.memberRole === "PROJECT_MANAGER" &&
          item.status === "ACTIVE" &&
          !item.effectiveTo,
      ),
      "V105固定浏览器项目缺少userId=1的当前项目经理事实",
    );
    const sourceRevision = expectSuccess(
      await api(page, {
        path: `/pms/project-templates/${fixedProject.lifecycleTemplateId}/revisions/${fixedProject.lifecycleTemplateRevisionNo}`,
      }),
      "V105冻结项目模板修订读取",
    );
    assert(
      sourceRevision.content?.tasks?.some(
        (item) =>
          item.taskDefinitionKey === "T-REQ-ANALYSIS" ||
          item.taskCode === "T-REQ-ANALYSIS",
      ),
      "V105冻结项目模板缺少T-REQ-ANALYSIS",
    );
    assert(
      fixedProject.lifecycleTemplateId === 992203040001 &&
        sourceRevision.id === 992203050001 &&
        sourceRevision.revisionNo === 2,
      "V105浏览器项目未冻结指定项目模板发布修订",
    );
    const frozenRequirementTask = sourceRevision.content.tasks.find(
      (item) =>
        item.taskDefinitionKey === "T-REQ-ANALYSIS" ||
        item.taskCode === "T-REQ-ANALYSIS",
    );
    const frozenBinding = JSON.parse(frozenRequirementTask.bindingConfig);
    assert(
      frozenRequirementTask.workBindingTypeCode === "BUSINESS_OBJECT" &&
        frozenRequirementTask.targetContextCode === "SOL" &&
        frozenRequirementTask.targetObjectType === "REQUIREMENT_ANALYSIS" &&
        frozenRequirementTask.targetObjectKey ===
          "PRE_04_REQUIREMENT_ANALYSIS" &&
        frozenBinding.dynamicFormTemplateId === compatibleTemplateId &&
        frozenBinding.dynamicFormTemplateRevisionId === compatibleRevisionId &&
        frozenBinding.dynamicFormRevisionNo === 1 &&
        frozenBinding.dynamicFormRevisionFactVersion === 1,
      "V105浏览器项目冻结WorkBinding四事实不精确",
    );
    const templateSuffix = `${Date.now()}${crypto.randomUUID()}`
      .replaceAll("-", "")
      .slice(-16);
    projectTemplateId = expectSuccess(
      await api(page, {
        path: "/pms/project-templates",
        method: "POST",
        body: {
          code: `FSOL003_BROWSER_${templateSuffix}`,
          name: `F-SOL-003浏览器WorkBinding验收-${templateSuffix}`,
          matchPriority: 9999,
          description: "公开REST自建；运行结束后停用",
        },
      }),
      "创建隔离项目模板草稿",
    );
    const acceptanceTemplate = { id: projectTemplateId, published: false };
    cleanup.templates.push(acceptanceTemplate);

    const incompatibleContent = setBindingRevision(
      sourceRevision.content,
      incompatibleTemplateId,
      incompatibleRevisionId,
    );
    expectSuccess(
      await api(page, {
        path: `/pms/project-templates/${projectTemplateId}`,
        method: "PUT",
        body: { content: incompatibleContent },
      }),
      "不兼容WorkBinding草稿保存",
    );
    const invalidPublishPath = `/admin-api/pms/project-templates/${projectTemplateId}/actions/publish`;
    expectedFailurePaths.add(invalidPublishPath);
    const incompatiblePublish = await api(page, {
      path: `/pms/project-templates/${projectTemplateId}/actions/publish`,
      method: "POST",
    });
    assert(
      incompatiblePublish.status >= 400 || incompatiblePublish.body?.code !== 0,
      "缺核心字段的动态表单修订被错误发布为PRE-04 WorkBinding",
    );
    assertions.push("INCOMPATIBLE_SCHEMA_PUBLICATION_REJECTED");

    const compatibleContent = setBindingRevision(
      sourceRevision.content,
      compatibleTemplateId,
      compatibleRevisionId,
    );
    expectSuccess(
      await api(page, {
        path: `/pms/project-templates/${projectTemplateId}`,
        method: "PUT",
        body: { content: compatibleContent },
      }),
      "兼容WorkBinding草稿保存",
    );
    expectSuccess(
      await api(page, {
        path: `/pms/project-templates/${projectTemplateId}/actions/publish`,
        method: "POST",
      }),
      "兼容WorkBinding发布",
    );
    acceptanceTemplate.published = true;
    assertions.push("COMPATIBLE_WORK_BINDING_PUBLISHED");

    await openRequirementWorkspace();
    assert(
      !(await page.getByText("选择模板", { exact: true }).isVisible()),
      "项目工作区错误显示模板选择",
    );
    assertions.push("PROJECT_USER_HAS_NO_TEMPLATE_SELECTION");

    const before = expectSuccess(await getWorkspace(), "工作区读取");
    assert(
      !before.draft && !before.currentEffective,
      "隔离项目不是空白PRE-04状态，请reset fixture",
    );
    await page
      .getByRole("button", { name: "创建需求分析草稿", exact: true })
      .click();
    await page.getByText("需求分析草稿已创建", { exact: true }).waitFor();
    const v1Workspace = expectSuccess(await getWorkspace(), "V1工作区读取");
    const v1Id = v1Workspace.draft.preparationId;
    let v1 = expectSuccess(await getDetail(v1Id), "V1详情读取");
    assert(
      v1.templateRevisionId === compatibleRevisionId,
      "V1未冻结指定WorkBinding修订",
    );
    assert(
      v1.formRulesJson?.filter((item) => item.type === "PmsFileArtifact")
        .length === 11,
      "V1未冻结11个核心附件槽位",
    );

    v1 = expectSuccess(await getDetail(v1Id), "必填阻断后V1读取");
    assert(v1.status === "DRAFT", "必填缺失仍完成V1");
    assert(
      v1.completionBlockers.some(
        (item) => item.code === "REQUIRED_VALUE_MISSING",
      ),
      "未投影必填阻断",
    );
    assert(
      !(await page
        .getByRole("button", { name: "完成并冻结当前草稿", exact: true })
        .isVisible()),
      "必填缺失时UI仍投影完成动作",
    );
    const blockedCompletePath = `/admin-api/api/v1/pms/preparations/${v1Id}/actions/submit`;
    expectedFailurePaths.add(blockedCompletePath);
    const blockedComplete = await api(page, {
      path: `/api/v1/pms/preparations/${v1Id}/actions/submit`,
      method: "POST",
      headers: {
        "Idempotency-Key": nowKey("required-blocked"),
        "If-Match": String(v1.dynamicFormInstanceVersion),
        "X-SOL-If-Match": String(v1.version),
      },
    });
    assert(
      blockedComplete.status >= 400 || blockedComplete.body?.code !== 0,
      "必填缺失的强制完成命令未被服务端拒绝",
    );
    assertions.push("REQUIRED_VALUE_BLOCKS_COMPLETION");

    await fillRichText("项目背景", "F-SOL-003 浏览器项目背景 V1");
    await fillRichText("项目目标", "F-SOL-003 浏览器项目目标 V1");
    await fillRichText("网络拓扑", "F-SOL-003 浏览器网络拓扑 V1");
    await saveForm();
    await uploadControlledFile(
      "fsol003-v1-background.txt",
      "F-SOL-003 V1 controlled file in MinIO",
    );
    await page.getByRole("button", { name: "刷新", exact: true }).click();
    await page
      .getByText("fsol003-v1-background.txt", { exact: true })
      .waitFor();
    let fileLifecycle = expectSuccess(
      await getDetail(v1Id),
      "首次上传后ACTIVE事实读取",
    );
    assert(
      controlledFacts(fileLifecycle, "PROJECT_BACKGROUND__ATTACHMENTS")
        .length === 1,
      "首次上传后PLT ACTIVE引用未即时成为唯一真值",
    );
    await uploadControlledFile(
      "fsol003-v1-background-v2.txt",
      "F-SOL-003 V1 controlled file replacement",
      page,
      true,
    );
    fileLifecycle = expectSuccess(
      await getDetail(v1Id),
      "换版后ACTIVE事实读取",
    );
    assert(
      controlledFacts(fileLifecycle, "PROJECT_BACKGROUND__ATTACHMENTS")
        .length === 1,
      "换版后PLT ACTIVE引用不唯一",
    );
    await page
      .getByRole("button", { name: "解绑", exact: true })
      .first()
      .click();
    const detachBox = page.locator(".el-message-box:visible");
    await detachBox.locator("input").fill("F-SOL-003浏览器验收解绑");
    await detachBox.getByRole("button", { name: "确定", exact: true }).click();
    await page.getByText("材料引用已解除", { exact: true }).waitFor();
    fileLifecycle = expectSuccess(
      await getDetail(v1Id),
      "解绑后ACTIVE事实读取",
    );
    assert(
      controlledFacts(fileLifecycle, "PROJECT_BACKGROUND__ATTACHMENTS")
        .length === 0,
      "解绑后PLT ACTIVE引用仍存在",
    );
    const consoleDiagnosticStart = consoleErrors.length;
    const pageDiagnosticStart = pageErrors.length;
    const requestDiagnosticStart = requestFailures.length;
    await uploadControlledFileWithUnknownResponse(
      "fsol003-v1-background-rebound.txt",
      "F-SOL-003 V1 controlled file rebound",
    );
    expectedDiagnostics.push(
      ...consoleErrors.splice(consoleDiagnosticStart),
      ...pageErrors.splice(pageDiagnosticStart),
      ...requestFailures.splice(requestDiagnosticStart),
    );
    await page.getByRole("button", { name: "刷新", exact: true }).click();
    v1 = expectSuccess(await getDetail(v1Id), "V1文件闭环读取");
    assert(
      controlledFacts(v1, "PROJECT_BACKGROUND__ATTACHMENTS").length === 1,
      "上传换版解绑重绑后ACTIVE集合不唯一",
    );
    const reboundFile = controlledFacts(
      v1,
      "PROJECT_BACKGROUND__ATTACHMENTS",
    )[0];
    const fileVersions = expectSuccess(
      await api(page, {
        path:
          `/api/v1/pms/files/${reboundFile.artifactId}/versions?` +
          new URLSearchParams({
            ownerContext: "PLATFORM",
            objectType: "DYNAMIC_FORM_INSTANCE",
            objectId: String(v1.dynamicFormInstanceId),
            purposeCode:
              "FORM_FIELD_ATTACHMENT/PROJECT_BACKGROUND__ATTACHMENTS",
            referenceKey: reboundFile.referenceKey,
            pageSize: "100",
          }).toString(),
      }),
      "受控文件版本历史读取",
    );
    const reboundVersion = fileVersions.items?.find(
      (item) => item.versionNo === reboundFile.versionNo,
    );
    assert(
      reboundVersion?.scanStatus === expectedScan,
      `受控文件扫描状态不是${expectedScan}`,
    );
    assert(
      !v1.completionBlockers.some((item) =>
        /ATTACHMENT_SET_(PENDING|UNKNOWN)/.test(item.code),
      ),
      "锁定基线不应产生SOL附件快照同步状态",
    );
    assertions.push("FILE_ACTIVE_FACTS_IMMEDIATE_AND_UNKNOWN_RETRY_RECOVERED");
    await screenshot("01-v1-filled-file-lifecycle-1440.png");

    await page
      .getByRole("button", { name: "完成并冻结当前草稿", exact: true })
      .click();
    await confirmMessageBox();
    await page
      .getByText("需求分析已完成并冻结为当前有效版本", { exact: true })
      .waitFor();
    v1 = expectSuccess(await getDetail(v1Id), "V1完成事实读取");
    assert(
      v1.status === "COMPLETED" && v1.currentEffective,
      "V1未冻结为唯一当前有效版",
    );
    assertions.push("V1_COMPLETED_WITH_FILE_LIFECYCLE");

    await page
      .getByRole("button", { name: "从当前有效版创建修订草稿", exact: true })
      .click();
    await confirmMessageBox();
    await page
      .getByText("修订草稿已创建，原完成版本保持不变", { exact: true })
      .waitFor();
    let v2Workspace = expectSuccess(await getWorkspace(), "V2工作区读取");
    const v2Id = v2Workspace.draft.preparationId;
    let v2 = expectSuccess(await getDetail(v2Id), "V2详情读取");
    assert(
      v2.dynamicFormInstanceId !== v1.dynamicFormInstanceId,
      "V2错误复用V1业务实例",
    );

    await fillRichText("项目目标", "F-SOL-003 浏览器项目目标 V2 响应未知");
    await page.getByRole("button", { name: "完成历史", exact: true }).click();
    await cancelMessageBox();
    assertions.push("DIRTY_CONTENT_BLOCKS_HISTORY_NAVIGATION");
    const consoleStart = consoleErrors.length;
    const pageStart = pageErrors.length;
    const requestStart = requestFailures.length;
    let unknownInjected = false;
    let patchCount = 0;
    const unknownRoute = "**/admin-api/api/v1/pms/preparations/*/form";
    await page.route(unknownRoute, async (route) => {
      if (route.request().method() === "PATCH") patchCount += 1;
      if (!unknownInjected && route.request().method() === "PATCH") {
        unknownInjected = true;
        await route.fetch();
        await route.abort();
        return;
      }
      await route.continue();
    });
    const patchAbortDiagnostics = waitForAbortedRequestDiagnostics(
      "PATCH",
      "/form",
    );
    await page.getByRole("button", { name: "保存表单", exact: true }).click();
    await page.getByText(/已确认需求分析表单保存成功|保存结果未知/).waitFor();
    await patchAbortDiagnostics;
    await page.waitForLoadState("networkidle");
    await page.unroute(unknownRoute);
    assert(
      unknownInjected && patchCount === 1,
      "响应未知未按同一意图恢复或产生重复PATCH",
    );
    expectedDiagnostics.push(
      ...consoleErrors.splice(consoleStart),
      ...pageErrors.splice(pageStart),
      ...requestFailures.splice(requestStart),
    );
    v2 = expectSuccess(await getDetail(v2Id), "响应未知后V2权威读取");
    assert(
      String(v2.values.PROJECT_OBJECTIVE).includes("响应未知"),
      "响应未知恢复未确认权威值",
    );
    assertions.push("RESPONSE_UNKNOWN_SAME_INTENT_RECOVERED");

    const file = controlledFacts(v2, "PROJECT_BACKGROUND__ATTACHMENTS")[0];
    assert(file, "V2克隆后缺少受控文件事实");
    const invalidated = await api(page, {
      path: `/api/v1/pms/files/${file.artifactId}/actions/invalidate`,
      method: "POST",
      headers: { "Idempotency-Key": nowKey("invalidate") },
      body: {
        versionNo: file.versionNo,
        expectedAvailabilityVersion: file.fileFactVersion.availabilityVersion,
        targetStatus: "UNAVAILABLE",
        reasonCode: "BROWSER_ACCEPTANCE",
        reasonDetail: "F-SOL-003真实浏览器文件失效阻断",
        ownerContext: "PLATFORM",
        objectType: "DYNAMIC_FORM_INSTANCE",
        objectId: String(v2.dynamicFormInstanceId),
        purposeCode: "FORM_FIELD_ATTACHMENT/PROJECT_BACKGROUND__ATTACHMENTS",
        referenceKey: file.referenceKey,
      },
    });
    expectSuccess(invalidated, "文件版本失效");
    await page.getByRole("button", { name: "刷新", exact: true }).click();
    v2 = expectSuccess(await getDetail(v2Id), "文件失效后V2读取");
    assert(
      v2.completionBlockers.some(
        (item) => item.code === "CONTROLLED_FILE_INVALID",
      ),
      "文件失效未阻断完成",
    );
    assert(!v2.allowedActions.includes("COMPLETE"), "文件失效仍投影COMPLETE");
    await screenshot("02-v2-file-invalid-blocked-1440.png");
    assertions.push("INVALID_FILE_BLOCKS_COMPLETION");

    await uploadControlledFile(
      "fsol003-v2-file-recovered.txt",
      "F-SOL-003 V2 recovers from invalid FileVersion",
      page,
      true,
    );
    const v2Recovered = expectSuccess(
      await getDetail(v2Id),
      "V2文件恢复ACTIVE事实读取",
    );
    assert(
      controlledFacts(v2Recovered, "PROJECT_BACKGROUND__ATTACHMENTS").length ===
        1 &&
        controlledFacts(v2Recovered, "PROJECT_BACKGROUND__ATTACHMENTS")[0]
          .versionNo > file.versionNo &&
        controlledFacts(v2Recovered, "PROJECT_BACKGROUND__ATTACHMENTS")[0]
          .availabilityStatus === "AVAILABLE" &&
        !v2Recovered.completionBlockers.some((item) =>
          /ATTACHMENT_SET_(PENDING|UNKNOWN)/.test(item.code),
        ),
      "V2上传有效新版本后PLT ACTIVE引用未即时恢复",
    );
    await page.getByRole("button", { name: "刷新", exact: true }).click();
    v2 = expectSuccess(await getDetail(v2Id), "文件恢复后V2读取");
    assert(
      !v2.completionBlockers.some(
        (item) => item.code === "CONTROLLED_FILE_INVALID",
      ),
      "上传新版本后文件失效阻断未解除",
    );
    await page
      .getByRole("button", { name: "完成并冻结当前草稿", exact: true })
      .click();
    await confirmMessageBox();
    await page
      .getByText("需求分析已完成并冻结为当前有效版本", { exact: true })
      .waitFor();
    v2 = expectSuccess(await getDetail(v2Id), "V2完成事实读取");
    assert(
      v2.status === "COMPLETED" && v2.currentEffective,
      "V2未替代V1成为当前有效版",
    );
    assertions.push("V2_RECOVERED_COMPLETED_AND_REPLACED_EFFECTIVE");

    await page.getByRole("button", { name: "完成历史", exact: true }).click();
    await page.getByText("业务版本 V1", { exact: false }).waitFor();
    await screenshot("03-history-1440.png");
    await closeVisibleDrawer("需求分析完成历史");
    await page.getByRole("button", { name: "完成历史", exact: true }).click();
    const compareButton = page
      .getByRole("button", { name: "与当前查看版对比", exact: true })
      .first();
    if (await compareButton.isVisible()) {
      await compareButton.click();
      await page.getByText(/V1/).first().waitFor();
      await screenshot("04-field-compare-1440.png");
      await closeVisibleDrawer("需求分析版本对比");
    }

    const responsive = [];
    for (const width of [320, 768, 1024, 1440]) {
      await page.setViewportSize({ width, height: width === 320 ? 760 : 900 });
      await openRequirementWorkspace();
      const metrics = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
      }));
      assert(
        metrics.scrollWidth <= metrics.clientWidth,
        `${width}px出现横向溢出`,
      );
      responsive.push({ width, ...metrics });
      await screenshot(`05-responsive-${width}.png`);
    }

    const sourceBeforeNegative = expectSuccess(
      await getDetail(v2Id),
      "负向前版本读取",
    );
    const verifyActor = async (actor, username, password, expectReadable) => {
      const actorContext = await browser.newContext({
        viewport: { width: 1024, height: 900 },
      });
      const actorPage = await actorContext.newPage();
      recordPage(actorPage, actor);
      try {
        await login(actorPage, username, password);
        await openRequirementWorkspace(actorPage);
        assert(
          !(await actorPage
            .getByRole("button", { name: "保存表单", exact: true })
            .isVisible()),
          `${actor}错误显示保存动作`,
        );
        assert(
          !(await actorPage
            .getByRole("button", { name: "完成并冻结当前草稿", exact: true })
            .isVisible()),
          `${actor}错误显示完成动作`,
        );
        const forcedPath = `/admin-api/api/v1/pms/preparations/${v2Id}/actions/create-draft`;
        expectedFailurePaths.add(forcedPath);
        const forced = await api(actorPage, {
          path: `/api/v1/pms/preparations/${v2Id}/actions/create-draft`,
          method: "POST",
          headers: {
            "Idempotency-Key": nowKey(`${actor}-create-draft`),
            "If-Match": String(sourceBeforeNegative.dynamicFormInstanceVersion),
            "X-SOL-If-Match": String(sourceBeforeNegative.version),
          },
        });
        assert(
          forced.status >= 400 || forced.body?.code !== 0,
          `${actor}强制创建修订未被拒绝`,
        );
        await screenshot(
          `06-${actor.toLowerCase()}-readonly-1024.png`,
          actorPage,
        );
        if (expectReadable)
          assert(
            await actorPage
              .getByRole("heading", { name: "需求分析", exact: true })
              .isVisible(),
            `${actor}应可读但工作区不可见`,
          );
      } finally {
        await actorContext.close();
      }
    };
    await page.setViewportSize({ width: 1024, height: 900 });
    await verifyActor(
      "READONLY_MEMBER",
      readonlyActor.username,
      readonlyActor.password,
      true,
    );
    await verifyActor(
      "NON_MANAGER",
      nonManagerActor.username,
      nonManagerActor.password,
      true,
    );

    const secondTenantLogin = await context.request.post(
      `${crossTenantApiUrl}/system/auth/login`,
      {
        headers: {
          "Content-Type": "application/json",
          "tenant-id": String(secondTenant.id),
        },
        data: {
          username: secondTenant.username,
          password: secondTenant.password,
        },
      },
    );
    const secondTenantLoginBody = await secondTenantLogin
      .json()
      .catch(() => undefined);
    assert(
      secondTenantLogin.status() === 200 &&
        secondTenantLoginBody?.code === 0 &&
        secondTenantLoginBody?.data?.accessToken,
      `第二租户公开登录失败：status=${secondTenantLogin.status()} code=${secondTenantLoginBody?.code}`,
    );
    const crossTenant = await context.request.get(
      `${crossTenantApiUrl}/api/v1/pms/preparations/${v2Id}?type=PRE_04`,
      {
        headers: {
          Authorization: `Bearer ${secondTenantLoginBody.data.accessToken}`,
          "tenant-id": String(secondTenant.id),
        },
      },
    );
    const crossBody = await crossTenant.json().catch(() => undefined);
    assert(
      crossTenant.status() >= 400 || crossBody?.code !== 0,
      "第二租户读取到源租户需求分析版本",
    );
    expectedNegativeResponses.push({
      actor: "SECOND_TENANT",
      method: "GET",
      status: crossTenant.status(),
      path: `/api/v1/pms/preparations/${v2Id}`,
      code: crossBody?.code,
    });
    assertions.push("READONLY_NON_MANAGER_SECOND_TENANT_DENIED");

    const sourceAfterNegative = expectSuccess(
      await getDetail(v2Id),
      "负向后版本读取",
    );
    assert(
      sourceAfterNegative.version === sourceBeforeNegative.version &&
        sourceAfterNegative.dynamicFormInstanceVersion ===
          sourceBeforeNegative.dynamicFormInstanceVersion,
      "负向动作产生成功副作用",
    );

    const pass =
      consoleErrors.length === 0 &&
      pageErrors.length === 0 &&
      requestFailures.length === 0 &&
      unexpectedResponses.length === 0;
    const result = {
      capturedAt: new Date().toISOString(),
      status: pass ? "PASSED" : "FAILED",
      browserVersion: browser.version(),
      baseUrl,
      apiUrl,
      scanMode: expectedScan,
      projectId,
      projectTemplateId,
      compatibleTemplateId,
      compatibleRevisionId,
      assertions,
      responsive,
      screenshots,
      expectedNegativeResponses,
      expectedDiagnostics,
      relevantHttp,
      consoleErrors,
      pageErrors,
      requestFailures,
      unexpectedResponses,
      pass,
    };
    fs.writeFileSync(
      path.join(outputDir, `browser-run-${runSlug}.json`),
      `${JSON.stringify(result, null, 2)}\n`,
    );
    console.log(JSON.stringify(result, null, 2));
    if (!result.pass) process.exitCode = 1;
  } catch (error) {
    const failure = {
      capturedAt: new Date().toISOString(),
      status: "FAILED",
      error:
        error instanceof Error ? error.stack || error.message : String(error),
      assertions,
      screenshots,
      expectedNegativeResponses,
      expectedDiagnostics,
      relevantHttp,
      consoleErrors,
      pageErrors,
      requestFailures,
      unexpectedResponses,
    };
    fs.writeFileSync(
      path.join(outputDir, `browser-run-${runSlug}-failed.json`),
      `${JSON.stringify(failure, null, 2)}\n`,
    );
    throw error;
  } finally {
    await cleanupActors();
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
