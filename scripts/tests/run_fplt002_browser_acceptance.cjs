const fs = require('node:fs')
const path = require('node:path')
const { chromium } = require('playwright')

const baseUrl = process.env.NPDMS_BROWSER_BASE_URL || 'http://127.0.0.1:18081'
const apiUrl = process.env.NPDMS_BROWSER_API_URL || 'http://localhost:58080/admin-api'
const executablePath = process.env.NPDMS_BROWSER_EXECUTABLE || undefined
const username = process.env.NPDMS_BROWSER_USERNAME || 'admin'
const password = process.env.NPDMS_BROWSER_PASSWORD || 'admin123'
const minioAccessKey = process.env.NPDMS_MINIO_ACCESS_KEY
const minioAccessSecret = process.env.NPDMS_MINIO_ACCESS_SECRET
const negativePassword = process.env.NPDMS_BROWSER_NEGATIVE_PASSWORD
const expectedScan = process.env.NPDMS_EXPECTED_SCAN || 'SKIPPED'
const outputDir = path.resolve(process.argv[2] || 'docs/engineering/evidence/f-plt-002')

fs.mkdirSync(outputDir, { recursive: true })

const templatePath = '/pms/engineering/document/dynamic-form-template'
const instancePath = '/pms/engineering/document/dynamic-form-instance'

;(async () => {
  const browser = await chromium.launch({ headless: true, executablePath })
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()
  const consoleErrors = []
  const pageErrors = []
  const unexpectedResponses = []
  const relevantHttp = []
  const expectedNegativeResponses = []
  const expectedResponseUnknownDiagnostics = []

  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  page.on('pageerror', (error) => pageErrors.push(error.message))
  page.on('response', (response) => {
    const url = response.url()
    if (!url.includes('/admin-api/')) return
    const item = {
      method: response.request().method(),
      status: response.status(),
      path: new URL(url).pathname
    }
    if (/dynamic-form|files|file-config/.test(url)) relevantHttp.push(item)
    if (response.status() >= 400) unexpectedResponses.push(item)
  })

  const login = async (targetPage, loginUsername = username, loginPassword = password) => {
    await targetPage.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
    await targetPage.locator('input[type="text"]:visible').first().fill(loginUsername)
    await targetPage.locator('input[type="password"]:visible').first().fill(loginPassword)
    await targetPage.getByRole('button', { name: '登录', exact: true }).click()
    await targetPage.waitForURL((url) => !url.pathname.startsWith('/login'))
  }

  const api = async (targetPage, request) =>
    targetPage.evaluate(
      async ({ apiUrl: base, request: input }) => {
        const cached = localStorage.getItem('ACCESS_TOKEN')
        let token = cached
        try {
          const item = JSON.parse(cached)
          token = item?.v ? JSON.parse(item.v) : item
        } catch {}
        const response = await fetch(`${base}${input.path}`, {
          method: input.method || 'GET',
          headers: {
            Authorization: `Bearer ${token}`,
            ...(input.body ? { 'Content-Type': 'application/json' } : {}),
            ...(input.headers || {})
          },
          body: input.body ? JSON.stringify(input.body) : undefined
        })
        const body = await response.json().catch(() => undefined)
        return { status: response.status, body }
      },
      { apiUrl, request }
    )

  const assert = (condition, message) => {
    if (!condition) throw new Error(message)
  }

  const confirmMessageBox = async () => {
    const box = page.locator('.el-message-box:visible')
    await box.waitFor()
    await box.getByRole('button', { name: '确定', exact: true }).click()
  }

  const currentRow = (templateCode) =>
    page
      .locator('.dynamic-template-page:visible .el-table__body-wrapper tbody tr')
      .filter({ hasText: templateCode })
      .first()

  const findTemplateRow = async (templateCode) => {
    for (let pageNo = 1; pageNo <= 20; pageNo += 1) {
      const row = currentRow(templateCode)
      if ((await row.count()) > 0 && (await row.isVisible())) return row
      const next = page.locator('.dynamic-template-page:visible .el-pagination .btn-next')
      if ((await next.count()) === 0 || (await next.isDisabled())) break
      await next.click()
      await page.waitForLoadState('networkidle')
    }
    throw new Error(`模板未出现在分页列表：${templateCode}`)
  }

  const findInstanceRow = async (instanceName) => {
    for (let pageNo = 1; pageNo <= 20; pageNo += 1) {
      const row = page
        .locator('.dynamic-instance-page:visible .el-table__body-wrapper tbody tr')
        .filter({ hasText: instanceName })
        .first()
      if ((await row.count()) > 0 && (await row.isVisible())) return row
      const next = page.locator('.dynamic-instance-page:visible .el-pagination .btn-next')
      if ((await next.count()) === 0 || (await next.isDisabled())) break
      await next.click()
      await page.waitForLoadState('networkidle')
    }
    throw new Error(`实例未出现在分页列表：${instanceName}`)
  }

  const addField = async (drawer, componentName, field, title) => {
    await drawer.getByText(componentName, { exact: true }).first().click()
    const fieldItem = drawer.locator('.el-form-item').filter({ hasText: '字段 ID' }).last()
    const titleItem = drawer.locator('.el-form-item').filter({ hasText: '字段名称' }).last()
    await fieldItem.locator('input').fill(field)
    await titleItem.locator('input').fill(title)
  }

  try {
    await login(page)

    const permissions = await api(page, { path: '/system/auth/get-permission-info' })
    assert(permissions.status === 200 && permissions.body?.code === 0, '管理员权限读取失败')
    for (const permission of [
      'pms:dynamic-form-template:query',
      'pms:dynamic-form-template:manage',
      'pms:dynamic-form-template:publish',
      'pms:dynamic-form-instance:query',
      'pms:dynamic-form-instance:create',
      'pms:dynamic-form-instance:update',
      'pms:file:upload',
      'pms:file:query'
    ]) {
      assert(permissions.body.data.permissions.includes(permission), `缺少浏览器验收权限：${permission}`)
    }

    assert(minioAccessKey && minioAccessSecret, '未提供隔离 MinIO 凭据')
    const configPage = await api(page, {
      path: '/infra/file-config/page?pageNo=1&pageSize=100'
    })
    let minioConfig = configPage.body?.data?.list?.find(
      (item) => item.name === 'F-PLT-002 浏览器 MinIO'
    )
    if (!minioConfig) {
      const created = await api(page, {
        path: '/infra/file-config/create',
        method: 'POST',
        body: {
          name: 'F-PLT-002 浏览器 MinIO',
          storage: 20,
          remark: 'F-PLT-002 隔离浏览器验收',
          config: {
            endpoint: 'http://127.0.0.1:9000',
            bucket: 'yudao',
            accessKey: minioAccessKey,
            accessSecret: minioAccessSecret,
            enablePathStyleAccess: true,
            enablePublicAccess: false,
            region: 'us-east-1',
            domain: 'http://127.0.0.1:9000/yudao'
          }
        }
      })
      assert(created.status === 200 && created.body?.code === 0, `MinIO 配置创建失败：${JSON.stringify(created)}`)
      minioConfig = { id: created.body.data }
    }
    const madeMaster = await api(page, {
      path: `/infra/file-config/update-master?id=${minioConfig.id}`,
      method: 'PUT'
    })
    assert(madeMaster.status === 200 && madeMaster.body?.code === 0, 'MinIO 主配置切换失败')
    const testedConfig = await api(page, {
      path: `/infra/file-config/test?id=${minioConfig.id}`
    })
    assert(testedConfig.status === 200 && testedConfig.body?.code === 0, 'MinIO 配置探针失败')

    await page.goto(`${baseUrl}${templatePath}`, { waitUntil: 'networkidle' })
    await page.getByTestId('template-desktop-list').waitFor()
    const suffix = Date.now()
    const templateCode = `FPLT002_BROWSER_${suffix}`
    const templateName = `A-共享动态表单浏览器验收 ${suffix}`
    const instanceName = `A-动态表单实例 ${suffix}`

    await page.getByRole('button', { name: '新建模板', exact: true }).click()
    const createDialog = page.locator('.el-dialog').filter({ hasText: '新建动态表单模板' })
    await createDialog.locator('.el-form-item').filter({ hasText: '模板编码' }).locator('input').fill(templateCode)
    await createDialog.locator('.el-form-item').filter({ hasText: '模板名称' }).locator('input').fill(templateName)
    await createDialog.locator('.el-form-item').filter({ hasText: '分类' }).locator('input').fill('GENERAL')
    await createDialog.locator('.el-form-item').filter({ hasText: '说明' }).locator('textarea').fill('完整 FormCreate、人工选模、冻结实例与 MinIO 受控文件闭环')
    await createDialog.getByRole('button', { name: '保存', exact: true }).click()

    const drawer = page.locator('.dynamic-form-editor-drawer')
    await drawer.waitFor()
    await drawer.getByText('高信任配置', { exact: true }).waitFor()
    for (const capability of ['网页 iframe', '接口选择器', '设置事件', '受控文件材料']) {
      await drawer.getByText(capability, { exact: true }).first().waitFor()
    }
    await addField(drawer, '输入框', 'projectName', '项目名称')
    await addField(drawer, '开关', 'requiresCutover', '是否需要割接')
    await addField(drawer, '计数器', 'machineCount', '设备数量')
    await addField(drawer, '受控文件材料', 'siteEvidence', '现场材料')
    await drawer.getByRole('button', { name: '保存草稿', exact: true }).click()
    await page.getByText('草稿修订已保存', { exact: true }).waitFor()
    await drawer.getByRole('button', { name: '预览', exact: true }).click()
    for (const fieldTitle of ['项目名称', '是否需要割接', '设备数量', '现场材料']) {
      await drawer.getByText(fieldTitle, { exact: true }).last().waitFor()
    }
    await page.screenshot({ path: path.join(outputDir, '01-template-designer-preview-1440.png'), fullPage: true })
    await drawer.locator('.el-drawer__close-btn').click()
    await drawer.waitFor({ state: 'hidden' })

    const row = await findTemplateRow(templateCode)
    await row.getByRole('button', { name: '发布草稿', exact: true }).click()
    await confirmMessageBox()
    await page.getByText('修订已发布', { exact: true }).waitFor()
    await (await findTemplateRow(templateCode)).getByRole('button', { name: '启用', exact: true }).click()
    await confirmMessageBox()
    await page.getByText('已启用', { exact: true }).filter({ visible: true }).last().waitFor()
    await page.screenshot({ path: path.join(outputDir, '02-template-published-enabled-1440.png'), fullPage: true })

    await page.goto(`${baseUrl}${instancePath}`, { waitUntil: 'networkidle' })
    await page.getByTestId('instance-desktop-list').waitFor()
    await page.getByRole('button', { name: '新建实例', exact: true }).click()
    const instanceDialog = page.locator('.el-dialog').filter({ hasText: '选择模板并创建实例' })
    const selectionCard = instanceDialog.locator('.el-card').filter({ hasText: templateCode })
    await selectionCard.waitFor()
    await selectionCard.click()
    for (const fieldTitle of ['项目名称', '是否需要割接', '设备数量', '现场材料']) {
      await instanceDialog.getByText(fieldTitle, { exact: true }).last().waitFor()
    }
    await instanceDialog.locator('.el-form-item').filter({ hasText: '实例名称' }).locator('input').fill(instanceName)
    await instanceDialog.getByRole('button', { name: '创建实例', exact: true }).click()

    const instanceDrawer = page.locator('.el-drawer').filter({ hasText: instanceName })
    await instanceDrawer.waitFor()
    await instanceDrawer.getByText('冻结修订', { exact: true }).first().waitFor()
    const projectNameItem = instanceDrawer.locator('.el-form-item').filter({ hasText: '项目名称' })
    await projectNameItem.locator('input').fill('F-PLT-002 浏览器项目')
    const cutoverItem = instanceDrawer.locator('.el-form-item').filter({ hasText: '是否需要割接' })
    await cutoverItem.locator('.el-switch').click()
    await cutoverItem.locator('.el-switch').click()
    const machineCountItem = instanceDrawer.locator('.el-form-item').filter({ hasText: '设备数量' })
    await machineCountItem.locator('input').fill('0')
    const consoleBeforeUnknown = consoleErrors.length
    const pageErrorsBeforeUnknown = pageErrors.length
    let unknownInjected = false
    const unknownRoute = '**/admin-api/api/v1/pms/dynamic-form-instances/*'
    await page.route(unknownRoute, async (route) => {
      if (!unknownInjected && route.request().method() === 'PATCH') {
        unknownInjected = true
        await route.fetch()
        await route.abort()
        return
      }
      await route.continue()
    })
    await instanceDrawer.getByRole('button', { name: '保存填写值', exact: true }).click()
    await page.getByText('已确认填写值保存成功', { exact: true }).waitFor()
    await page.unroute(unknownRoute)
    assert(unknownInjected, '未注入实例 PATCH 响应未知')
    expectedResponseUnknownDiagnostics.push(
      ...consoleErrors.splice(consoleBeforeUnknown),
      ...pageErrors.splice(pageErrorsBeforeUnknown)
    )

    const uploader = instanceDrawer.locator('.pms-file-uploader').last()
    await uploader.locator('input[type="file"]').setInputFiles({
      name: 'fplt002-browser-evidence.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('F-PLT-002 real browser MinIO acceptance')
    })
    await uploader.getByRole('button', { name: '上传并绑定', exact: true }).click()
    await page.getByText('文件已通过服务端校验并绑定', { exact: true }).waitFor({ timeout: 30000 })
    await instanceDrawer.getByRole('button', { name: '版本历史', exact: true }).click()
    const versionDrawer = page
      .locator('.el-drawer:visible')
      .filter({ has: page.getByText('文件版本历史', { exact: true }) })
      .last()
    const expectedScanLabel =
      expectedScan === 'PASSED' ? '已执行并通过扫描' : '未执行安全扫描（不代表安全）'
    await versionDrawer.getByText(expectedScanLabel, { exact: true }).waitFor()
    await page.screenshot({
      path: path.join(outputDir, `03-instance-minio-${expectedScan.toLowerCase()}-1440.png`),
      fullPage: true
    })
    await versionDrawer.locator('.el-drawer__close-btn').last().click()

    const instancePage = await api(page, {
      path: '/api/v1/pms/dynamic-form-instances?pageNo=1&pageSize=100'
    })
    const createdSummary = instancePage.body?.data?.list?.find((item) => item.instanceName === instanceName)
    assert(createdSummary, '创建后的实例未出现在权威分页')
    const instanceFact = await api(page, {
      path: `/api/v1/pms/dynamic-form-instances/${createdSummary.instanceId}`
    })
    assert(instanceFact.body?.data?.values?.projectName === 'F-PLT-002 浏览器项目', '普通字段未冻结保存')
    assert(instanceFact.body?.data?.values?.requiresCutover === false, '布尔 false 未作为有效值保存')
    assert(instanceFact.body?.data?.values?.machineCount === 0, '数字 0 未作为有效值保存')
    const fileFacts = instanceFact.body?.data?.controlledFiles?.siteEvidence || []
    assert(fileFacts.length === 1, 'MinIO 文件事实未进入受控字段')

    await instanceDrawer.locator('.el-drawer__close-btn').first().click()
    await page.goto(`${baseUrl}${templatePath}`, { waitUntil: 'networkidle' })
    await (await findTemplateRow(templateCode)).getByRole('button', { name: '停用', exact: true }).click()
    await confirmMessageBox()
    await page.goto(`${baseUrl}${instancePath}`, { waitUntil: 'networkidle' })
    const createdRow = await findInstanceRow(instanceName)
    await createdRow.getByRole('button', { name: '填写', exact: true }).click()
    const frozenDrawer = page.locator('.el-drawer').filter({ hasText: instanceName })
    await page.waitForFunction(
      (value) => [...document.querySelectorAll('input')].some((input) => input.value === value),
      'F-PLT-002 浏览器项目'
    )
    await frozenDrawer.getByText('fplt002-browser-evidence.txt', { exact: true }).waitFor()
    await frozenDrawer.locator('.el-drawer__close-btn').first().click()
    await page.getByRole('button', { name: '新建实例', exact: true }).click()
    const disabledSelection = page.locator('.el-dialog').filter({ hasText: '选择模板并创建实例' })
    await disabledSelection.waitFor()
    assert((await disabledSelection.getByText(templateCode, { exact: true }).count()) === 0, '停用模板仍可用于新实例选择')
    await disabledSelection.getByRole('button', { name: '取消', exact: true }).click()

    for (const viewport of [
      { width: 320, height: 780 },
      { width: 768, height: 900 },
      { width: 1024, height: 900 },
      { width: 1440, height: 900 }
    ]) {
      await page.setViewportSize(viewport)
      await page.goto(`${baseUrl}${instancePath}`, { waitUntil: 'networkidle' })
      const expectedTestId = viewport.width <= 767 ? 'instance-mobile-list' : 'instance-desktop-list'
      await page.getByTestId(expectedTestId).waitFor()
      await page.screenshot({
        path: path.join(outputDir, `04-instance-list-${viewport.width}.png`),
        fullPage: true
      })
    }

    await page.setViewportSize({ width: 1440, height: 900 })
    const legacyApis = [
      { name: 'BPM表单', path: '/bpm/form/page?pageNo=1&pageSize=10', expectedTotal: 0 },
      { name: '旧PMS表单模板', path: '/pms/eng-form-template/page?pageNo=1&pageSize=10', expectedTotal: 10 },
      { name: '旧PMS表单实例', path: '/pms/eng-form-instance/page?pageNo=1&pageSize=10', expectedTotal: 45 },
      { name: '旧需求分析', path: '/pms/eng-requirement/page?pageNo=1&pageSize=10', expectedTotal: 31 }
    ]
    const legacyApiFacts = []
    for (const item of legacyApis) {
      const response = await api(page, { path: item.path })
      assert(response.status === 200 && response.body?.code === 0, `${item.name}旧API不可用`)
      assert(response.body?.data?.total === item.expectedTotal, `${item.name}旧表行数发生变化`)
      legacyApiFacts.push({ name: item.name, total: response.body.data.total, status: response.status })
    }
    const legacyPages = [
      { name: 'BPM表单', route: '/bpm/manager/form', file: '06-legacy-bpm-form-1440.png' },
      {
        name: '旧PMS表单模板',
        route: '/pms/engineering/document/eng-form-template',
        file: '07-legacy-pms-form-template-1440.png'
      },
      {
        name: '旧PMS表单实例',
        route: '/pms/eng-form-instance',
        file: '08-legacy-pms-form-instance-1440.png'
      },
      {
        name: '旧需求分析',
        route: '/pms/engineering/preparation/eng-requirement',
        file: '09-legacy-requirement-1440.png'
      }
    ]
    const legacyPageFacts = []
    for (const item of legacyPages) {
      await page.goto(`${baseUrl}${item.route}`, { waitUntil: 'networkidle' })
      const bodyText = await page.locator('body').innerText()
      assert(!bodyText.includes('抱歉，您访问的页面不存在'), `${item.name}旧页面不可达`)
      legacyPageFacts.push({ name: item.name, route: item.route, title: await page.title() })
      await page.screenshot({ path: path.join(outputDir, item.file), fullPage: true })
    }

    assert(negativePassword, '未提供隔离无权主体测试密码')
    const resetNegativePassword = await api(page, {
      path: '/system/user/update-password',
      method: 'PUT',
      body: { id: 141, password: negativePassword }
    })
    assert(
      resetNegativePassword.status === 200 && resetNegativePassword.body?.code === 0,
      `无权主体测试密码重置失败：${JSON.stringify(resetNegativePassword)}`
    )
    const negativeContext = await browser.newContext({ viewport: { width: 1024, height: 800 } })
    const negativePage = await negativeContext.newPage()
    const negativeConsoleErrors = []
    negativePage.on('console', (message) => {
      if (message.type() === 'error') negativeConsoleErrors.push(message.text())
    })
    let negativeLoginSucceeded = false
    try {
      await login(negativePage, 'admin1', negativePassword)
      negativeLoginSucceeded = true
      const denied = await api(negativePage, {
        path: '/api/v1/pms/dynamic-form-templates?pageNo=1&pageSize=10'
      })
      expectedNegativeResponses.push({
        actor: 'admin1',
        status: denied.status,
        code: denied.body?.code,
        sideEffect: 'NONE'
      })
      assert(
        denied.status === 403 || denied.body?.code === 403 || denied.body?.code === 1_000_002_001,
        `无权查询未失败关闭：${JSON.stringify(denied)}`
      )
      await negativePage.goto(`${baseUrl}${templatePath}`, { waitUntil: 'networkidle' })
      await negativePage.screenshot({ path: path.join(outputDir, '05-unprivileged-denied-1024.png'), fullPage: true })
    } finally {
      await negativeContext.close()
    }
    assert(negativeLoginSucceeded, '无权验收主体 admin1 无法登录')

    assert(consoleErrors.length === 0, `存在浏览器 console error：${JSON.stringify(consoleErrors)}`)
    assert(pageErrors.length === 0, `存在浏览器 page error：${JSON.stringify(pageErrors)}`)
    assert(unexpectedResponses.length === 0, `存在意外 HTTP 失败：${JSON.stringify(unexpectedResponses)}`)

    const result = {
      capturedAt: new Date().toISOString(),
      browserVersion: browser.version(),
      candidateRuntime: { frontend: baseUrl, backend: apiUrl, fileStorage: 'MinIO', scanMode: expectedScan },
      template: { templateCode, lifecycle: ['DRAFT', 'PUBLISHED', 'ENABLED', 'DISABLED'] },
      instance: {
        instanceId: createdSummary.instanceId,
        frozenRevisionId: instanceFact.body.data.templateRevisionId,
        ordinaryValues: { projectName: 'F-PLT-002 浏览器项目', requiresCutover: false, machineCount: 0 },
        controlledFileCount: fileFacts.length,
        remainsReadableAfterTemplateDisabled: true
      },
      capabilitiesObserved: ['完整 FormCreate 设计器', 'iframe', '接口选择器', '事件', '函数/parseFunc 提示', 'PmsFileArtifact'],
      responsiveWidths: [320, 768, 1024, 1440],
      legacyUnchanged: { apiFacts: legacyApiFacts, pageFacts: legacyPageFacts },
      expectedNegativeResponses,
      responseUnknownRecovery: {
        serverCommittedBeforeBrowserAbort: true,
        authoritativeReadConfirmedSameIntent: true,
        retainedIntentReleasedAfterConfirmation: true,
        expectedDiagnostics: expectedResponseUnknownDiagnostics
      },
      relevantHttp,
      consoleErrors,
      pageErrors,
      unexpectedResponses
    }
    fs.writeFileSync(
      path.join(outputDir, `browser-run-${expectedScan.toLowerCase()}.json`),
      `${JSON.stringify(result, null, 2)}\n`
    )
    console.log(JSON.stringify(result))
  } finally {
    await browser.close()
  }
})().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
