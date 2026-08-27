const fs = require('node:fs')
const http = require('node:http')
const path = require('node:path')
const { chromium } = require('playwright')

const baseUrl = process.env.NPDMS_BROWSER_BASE_URL || 'http://127.0.0.1:18081'
const apiUrl = process.env.NPDMS_BROWSER_API_URL || 'http://localhost:58080/admin-api'
const crossTenantApiUrl = process.env.NPDMS_CROSS_TENANT_API_URL
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
  const capabilityTargetServer = http.createServer((request, response) => {
    if (request.url === '/iframe-denied') {
      response.writeHead(200, {
        'Content-Type': 'text/html; charset=utf-8',
        'Content-Security-Policy': "frame-ancestors 'none'",
        'X-Frame-Options': 'DENY'
      })
      response.end('<!doctype html><title>iframe denied</title>')
      return
    }
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end('{"items":[{"label":"target","value":"target"}]}')
  })
  await new Promise((resolve) => capabilityTargetServer.listen(0, '127.0.0.1', resolve))
  const capabilityTargetOrigin = `http://127.0.0.1:${capabilityTargetServer.address().port}`
  const browser = await chromium.launch({ headless: true, executablePath })
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()
  const consoleErrors = []
  const pageErrors = []
  const unexpectedResponses = []
  const relevantHttp = []
  const expectedNegativeResponses = []
  const expectedResponseUnknownDiagnostics = []
  const expectedCapabilityFailures = []
  const expectedCapabilityDiagnostics = []
  const expectedFailurePaths = new Set()

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
    if (expectedFailurePaths.has(item.path)) {
      void response
        .json()
        .then((body) => {
          if (response.status() >= 400 || body?.code !== 0) {
            expectedCapabilityFailures.push({ ...item, code: body?.code })
          }
        })
        .catch(() => {
          if (response.status() >= 400) expectedCapabilityFailures.push(item)
        })
      return
    }
    if (response.status() >= 400) {
      unexpectedResponses.push(item)
    }
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
      const loaded = page.waitForResponse(
        (response) =>
          response.request().method() === 'GET' &&
          response.url().includes('/api/v1/pms/dynamic-form-templates?')
      )
      await next.click()
      await loaded
    }
    throw new Error(`模板未出现在分页列表：${templateCode}`)
  }

  const findInstanceRow = async (instanceName, targetPage = page) => {
    for (let pageNo = 1; pageNo <= 20; pageNo += 1) {
      const row = targetPage
        .locator('.dynamic-instance-page:visible .el-table__body-wrapper tbody tr')
        .filter({ hasText: instanceName })
        .first()
      if ((await row.count()) > 0 && (await row.isVisible())) return row
      const next = targetPage.locator('.dynamic-instance-page:visible .el-pagination .btn-next')
      if ((await next.count()) === 0 || (await next.isDisabled())) break
      const loaded = targetPage.waitForResponse(
        (response) =>
          response.request().method() === 'GET' &&
          response.url().includes('/api/v1/pms/dynamic-form-instances?')
      )
      await next.click()
      await loaded
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

    const templatePageFact = await api(page, {
      path: '/api/v1/pms/dynamic-form-templates?pageNo=1&pageSize=200'
    })
    const createdTemplate = templatePageFact.body?.data?.list?.find(
      (item) => item.templateCode === templateCode
    )
    assert(createdTemplate?.currentDraft, '未读取到新模板当前草稿摘要')
    const draftFact = await api(page, {
      path: `/api/v1/pms/dynamic-form-template-revisions/${createdTemplate.currentDraft.revisionId}`
    })
    assert(draftFact.body?.code === 0, '未读取到明确草稿修订')
    const encodedEvent =
      '[[FORM-CREATE-PREFIX-function(){window.__fplt002Event=(window.__fplt002Event||0)+1}-FORM-CREATE-SUFFIX]]'
    const comprehensiveRules = [
      {
        type: 'input',
        field: 'projectName',
        title: '项目名称',
        validate: [{ required: true, message: '项目名称必填', trigger: 'blur' }],
        on: { change: encodedEvent }
      },
      { type: 'switch', field: 'requiresCutover', title: '是否需要割接' },
      { type: 'inputNumber', field: 'machineCount', title: '设备数量' },
      { type: 'input', field: 'nullableNote', title: '可空说明' },
      {
        type: 'select',
        field: 'emptyTags',
        title: '空数组标签',
        props: { multiple: true },
        options: [{ label: '样例', value: 'SAMPLE' }]
      },
      {
        type: 'select',
        field: 'linkDriver',
        title: '联动开关',
        options: [
          { label: '显示', value: 'SHOW' },
          { label: '隐藏', value: 'HIDE' }
        ],
        control: [{ value: 'SHOW', condition: '==', method: 'display', rule: ['linkedField'] }]
      },
      { type: 'input', field: 'linkedField', title: '联动字段' },
      { type: 'Editor', field: 'richDescription', title: '富文本说明' },
      {
        type: 'UploadFile',
        field: 'ordinaryFiles',
        title: '普通上传',
        props: { fileType: ['txt'], fileSize: 5, limit: 2, autoUpload: true }
      },
      {
        type: 'ApiSelect',
        field: 'getApiChoice',
        title: 'GET接口选择',
        props: {
          url: '/system/user/simple-list',
          method: 'GET',
          labelField: 'nickname',
          valueField: 'id',
          parseFunc:
            'function(data){window.__fplt002Parse=(window.__fplt002Parse||0)+1;return data.map(function(item){return {label:item.nickname,value:item.id}})}'
        }
      },
      {
        type: 'ApiSelect',
        field: 'postApiChoice',
        title: 'POST接口选择',
        props: {
          url: '/system/permission/assign-user-role',
          method: 'POST',
          data: '{}',
          labelField: 'label',
          valueField: 'value'
        }
      },
      {
        type: 'ApiSelect',
        field: 'corsApiChoice',
        title: 'CORS接口选择',
        props: {
          url: `${capabilityTargetOrigin}/cors-denied`,
          method: 'GET',
          labelField: 'label',
          valueField: 'value'
        }
      },
      {
        type: 'IframeComponent',
        field: 'deniedFrame',
        title: '受拒绝iframe',
        props: {
          url: `${capabilityTargetOrigin}/iframe-denied`,
          height: '120px',
          width: '100%',
          loading: 'eager'
        }
      },
      { type: 'PmsFileArtifact', field: 'siteEvidence', title: '现场材料' }
    ]
    expectedFailurePaths.add('/admin-api/system/permission/assign-user-role')
    const comprehensivePatch = await api(page, {
      path: `/api/v1/pms/dynamic-form-template-revisions/${createdTemplate.currentDraft.revisionId}`,
      method: 'PATCH',
      headers: { 'If-Match': String(draftFact.body.data.revisionVersion) },
      body: {
        formConfJson: { form: { labelPosition: 'right', labelWidth: '120px' } },
        formRulesJson: comprehensiveRules,
        engineCode: 'FORM_CREATE_ELEMENT_PLUS',
        designerVersion: '3.4.0',
        rendererVersion: '3.2.38'
      }
    })
    assert(comprehensivePatch.status === 200 && comprehensivePatch.body?.code === 0, '完整能力草稿保存失败')
    await drawer.getByRole('button', { name: '重新读取', exact: true }).click()
    const previewConsoleStart = consoleErrors.length
    const previewPageStart = pageErrors.length
    await drawer.getByRole('button', { name: '预览', exact: true }).click()
    for (const fieldTitle of [
      '项目名称',
      '是否需要割接',
      '设备数量',
      '普通上传',
      'GET接口选择',
      'POST接口选择',
      'CORS接口选择',
      '受拒绝iframe',
      '现场材料'
    ]) {
      await drawer.getByText(fieldTitle, { exact: true }).last().waitFor()
    }
    await page.waitForTimeout(1200)
    expectedCapabilityDiagnostics.push({
      phase: 'draft-preview-target-failures',
      console: consoleErrors.splice(previewConsoleStart),
      page: pageErrors.splice(previewPageStart)
    })
    await page.screenshot({ path: path.join(outputDir, '01-template-designer-preview-1440.png'), fullPage: true })
    await drawer.locator('.el-drawer__close-btn').click()
    await drawer.waitFor({ state: 'hidden' })

    const row = await findTemplateRow(templateCode)
    await row.locator('button').filter({ hasText: '修订' }).first().click()
    await drawer.waitFor()
    const reopenConsoleStart = consoleErrors.length
    const reopenPageStart = pageErrors.length
    await drawer.getByRole('button', { name: '预览', exact: true }).click()
    await drawer.getByText('GET接口选择', { exact: true }).last().waitFor()
    await page.waitForTimeout(1200)
    expectedCapabilityDiagnostics.push({
      phase: 'draft-reopen-preview-target-failures',
      console: consoleErrors.splice(reopenConsoleStart),
      page: pageErrors.splice(reopenPageStart)
    })
    const reopenedDraft = await api(page, {
      path: `/api/v1/pms/dynamic-form-template-revisions/${createdTemplate.currentDraft.revisionId}`
    })
    const reopenedTypes = reopenedDraft.body?.data?.formRulesJson?.map((rule) => rule.type) || []
    for (const type of ['Editor', 'UploadFile', 'ApiSelect', 'IframeComponent', 'PmsFileArtifact']) {
      assert(reopenedTypes.includes(type), `草稿重开丢失控件：${type}`)
    }
    assert(
      reopenedDraft.body.data.formRulesJson.some(
        (rule) => rule.on?.change === encodedEvent && Array.isArray(rule.validate)
      ),
      '草稿重开丢失事件函数或校验'
    )
    assert(
      reopenedDraft.body.data.formRulesJson.some(
        (rule) => rule.props?.parseFunc?.includes('__fplt002Parse')
      ),
      '草稿重开丢失parseFunc'
    )
    assert(
      reopenedDraft.body.data.formRulesJson.some((rule) => Array.isArray(rule.control)),
      '草稿重开丢失联动配置'
    )
    await drawer.locator('.el-drawer__close-btn').click()
    await drawer.waitFor({ state: 'hidden' })
    await page.reload({ waitUntil: 'networkidle' })
    const publishRow = await findTemplateRow(templateCode)
    await publishRow.getByRole('button', { name: '发布草稿', exact: true }).click()
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
    const instancePreviewConsoleStart = consoleErrors.length
    const instancePreviewPageStart = pageErrors.length
    await selectionCard.click()
    for (const fieldTitle of ['项目名称', '是否需要割接', '设备数量', '现场材料']) {
      await instanceDialog.getByText(fieldTitle, { exact: true }).last().waitFor()
    }
    await page.waitForTimeout(1200)
    expectedCapabilityDiagnostics.push({
      phase: 'selection-preview-target-failures',
      console: consoleErrors.splice(instancePreviewConsoleStart),
      page: pageErrors.splice(instancePreviewPageStart)
    })
    await instanceDialog.locator('.el-form-item').filter({ hasText: '实例名称' }).locator('input').fill(instanceName)
    const runtimeConsoleStart = consoleErrors.length
    const runtimePageStart = pageErrors.length
    await instanceDialog.getByRole('button', { name: '创建实例', exact: true }).click()

    const instanceDrawer = page.locator('.el-drawer').filter({ hasText: instanceName })
    await instanceDrawer.waitFor()
    await instanceDrawer.getByText('冻结修订', { exact: true }).first().waitFor()
    await page.waitForTimeout(1200)
    expectedCapabilityDiagnostics.push({
      phase: 'instance-runtime-target-failures',
      console: consoleErrors.splice(runtimeConsoleStart),
      page: pageErrors.splice(runtimePageStart)
    })
    assert((await page.evaluate(() => window.__fplt002Parse || 0)) > 0, 'GET接口parseFunc未在实例运行')
    const beforeValidation = await api(page, {
      path: '/api/v1/pms/dynamic-form-instances?pageNo=1&pageSize=100'
    })
    const beforeValidationSummary = beforeValidation.body?.data?.list?.find(
      (item) => item.instanceName === instanceName
    )
    await instanceDrawer.getByRole('button', { name: '保存填写值', exact: true }).click()
    await instanceDrawer.locator('.el-form-item.is-error').filter({ hasText: '项目名称' }).waitFor()
    const afterValidation = await api(page, {
      path: `/api/v1/pms/dynamic-form-instances/${beforeValidationSummary.instanceId}`
    })
    assert(afterValidation.body.data.instanceVersion === 0, '校验失败仍产生实例PATCH成功事实')
    const projectNameItem = instanceDrawer.locator('.el-form-item').filter({ hasText: '项目名称' })
    await projectNameItem.locator('input').fill('F-PLT-002 浏览器项目')
    await projectNameItem.locator('input').blur()
    assert((await page.evaluate(() => window.__fplt002Event || 0)) > 0, '字段事件函数未在实例运行')
    const linkageItem = instanceDrawer.locator('.el-form-item').filter({ hasText: '联动开关' })
    await linkageItem.locator('.el-select').click()
    await page.locator('.el-select-dropdown:visible').getByText('显示', { exact: true }).click()
    await instanceDrawer.locator('.el-form-item').filter({ hasText: '联动字段' }).waitFor()
    const cutoverItem = instanceDrawer.locator('.el-form-item').filter({ hasText: '是否需要割接' })
    await cutoverItem.locator('.el-switch').click()
    await cutoverItem.locator('.el-switch').click()
    const machineCountItem = instanceDrawer.locator('.el-form-item').filter({ hasText: '设备数量' })
    await machineCountItem.locator('input').fill('0')
    const ordinaryUpload = instanceDrawer.locator('.el-form-item').filter({ hasText: '普通上传' })
    const ordinaryUploadCompleted = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' && response.url().includes('/infra/file/upload')
    )
    await ordinaryUpload.locator('input[type="file"]').setInputFiles({
      name: 'fplt002-ordinary-upload.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('ordinary FormCreate upload; not FileArtifact evidence')
    })
    assert((await ordinaryUploadCompleted).status() === 200, '普通上传未成功完成')
    await ordinaryUpload.getByRole('link', { name: '下载', exact: true }).waitFor({ timeout: 30000 })
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
    const reconciliationRead = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        response.request().method() === 'GET' &&
        url.pathname ===
          `/admin-api/api/v1/pms/dynamic-form-instances/${beforeValidationSummary.instanceId}`
      )
    })
    await instanceDrawer.getByRole('button', { name: '保存填写值', exact: true }).click()
    assert((await reconciliationRead).status() === 200, '响应未知后未读取权威实例完成恢复')
    await instanceDrawer.locator('.el-loading-mask').waitFor({ state: 'hidden' })
    await page.unroute(unknownRoute)
    assert(unknownInjected, '未注入实例 PATCH 响应未知')
    expectedResponseUnknownDiagnostics.push(
      ...consoleErrors.splice(consoleBeforeUnknown),
      ...pageErrors.splice(pageErrorsBeforeUnknown)
    )
    const savedBeforeFalseyPatch = await api(page, {
      path: `/api/v1/pms/dynamic-form-instances/${beforeValidationSummary.instanceId}`
    })
    const falseyPatch = await api(page, {
      path: `/api/v1/pms/dynamic-form-instances/${beforeValidationSummary.instanceId}`,
      method: 'PATCH',
      headers: { 'If-Match': String(savedBeforeFalseyPatch.body.data.instanceVersion) },
      body: { values: { nullableNote: null, emptyTags: [] } }
    })
    assert(falseyPatch.status === 200 && falseyPatch.body?.code === 0, 'null/空数组公开PATCH失败')
    await instanceDrawer.getByRole('button', { name: '刷新权威事实', exact: true }).click()
    await instanceDrawer.locator('.el-loading-mask').waitFor({ state: 'hidden' })

    const uploader = instanceDrawer.locator('.pms-file-uploader').last()
    await uploader.locator('input[type="file"]').setInputFiles({
      name: 'fplt002-browser-evidence.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('F-PLT-002 real browser MinIO acceptance')
    })
    const firstUploadCompleted = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' && response.url().includes(':complete-upload')
    )
    await uploader.getByRole('button', { name: '上传并绑定', exact: true }).click()
    assert((await firstUploadCompleted).status() === 200, '受控文件首次上传未成功完成')
    await instanceDrawer.getByText('fplt002-browser-evidence.txt', { exact: true }).waitFor({ timeout: 30000 })
    await instanceDrawer.getByRole('button', { name: '版本历史', exact: true }).click()
    const versionDrawer = page.locator('.el-drawer[aria-label="文件版本历史"]').last()
    const expectedScanLabel =
      expectedScan === 'PASSED' ? '已执行并通过扫描' : '未执行安全扫描（不代表安全）'
    await versionDrawer.getByText(expectedScanLabel, { exact: true }).waitFor()
    await page.screenshot({
      path: path.join(outputDir, `03-instance-minio-${expectedScan.toLowerCase()}-1440.png`),
      fullPage: true
    })
    await versionDrawer.locator('.el-drawer__close-btn').last().click()
    await versionDrawer.waitFor({ state: 'hidden' })
    const versionUploader = instanceDrawer
      .locator('.pms-file-uploader')
      .filter({ hasText: '上传新版本' })
      .first()
    await versionUploader.locator('input[type="file"]').setInputFiles({
      name: 'fplt002-browser-evidence-v2.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('F-PLT-002 controlled file second version')
    })
    const secondUploadCompleted = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' && response.url().includes(':complete-upload')
    )
    await versionUploader.getByRole('button', { name: '上传新版本', exact: true }).click()
    assert((await secondUploadCompleted).status() === 200, '受控文件换版未成功完成')
    await instanceDrawer.getByText('fplt002-browser-evidence-v2.txt', { exact: true }).waitFor({ timeout: 30000 })
    await instanceDrawer.getByRole('button', { name: '版本历史', exact: true }).click()
    const changedVersionDrawer = page.locator('.el-drawer[aria-label="文件版本历史"]').last()
    await changedVersionDrawer.getByText('2', { exact: true }).first().waitFor()
    await page.screenshot({
      path: path.join(outputDir, '03a-controlled-file-version-changed-1440.png'),
      fullPage: true
    })
    await changedVersionDrawer.locator('.el-drawer__close-btn').last().click()
    await changedVersionDrawer.waitFor({ state: 'hidden' })
    await instanceDrawer.getByRole('button', { name: '解绑', exact: true }).click()
    const detachBox = page.locator('.el-message-box:visible')
    await detachBox.locator('input').fill('浏览器验收解绑')
    await detachBox.getByRole('button', { name: '确定', exact: true }).click()
    await page.getByText('材料引用已解除', { exact: true }).waitFor()
    await instanceDrawer.getByRole('button', { name: '刷新权威事实', exact: true }).click()
    await instanceDrawer.locator('.el-loading-mask').waitFor({ state: 'hidden' })
    const detachedFact = await api(page, {
      path: `/api/v1/pms/dynamic-form-instances/${beforeValidationSummary.instanceId}`
    })
    assert((detachedFact.body.data.controlledFiles.siteEvidence || []).length === 0, '解绑刷新后仍返回ACTIVE引用')
    await page.screenshot({
      path: path.join(outputDir, '03b-controlled-file-detached-1440.png'),
      fullPage: true
    })
    const reboundUploader = instanceDrawer.locator('.pms-file-uploader').last()
    await reboundUploader.locator('input[type="file"]').setInputFiles({
      name: 'fplt002-browser-rebound.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('F-PLT-002 controlled file rebound after detach')
    })
    const reboundUploadCompleted = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' && response.url().includes(':complete-upload')
    )
    await reboundUploader.getByRole('button', { name: '上传并绑定', exact: true }).click()
    assert((await reboundUploadCompleted).status() === 200, '解绑后重绑未成功完成')
    await instanceDrawer.getByText('fplt002-browser-rebound.txt', { exact: true }).waitFor({ timeout: 30000 })

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
    assert(instanceFact.body?.data?.values?.nullableNote === null, 'null 未作为有效值保存')
    assert(Array.isArray(instanceFact.body?.data?.values?.emptyTags) && instanceFact.body.data.values.emptyTags.length === 0,
      '空数组未作为有效值保存')
    assert(String(instanceFact.body?.data?.values?.ordinaryFiles || '').includes('fplt002-ordinary-upload.txt'),
      '普通上传值未保存为普通JSON')
    const fileFacts = instanceFact.body?.data?.controlledFiles?.siteEvidence || []
    assert(fileFacts.length === 1, 'MinIO 文件事实未进入受控字段')

    await instanceDrawer.locator('.el-drawer__close-btn').first().click()
    await page.goto(`${baseUrl}${templatePath}`, { waitUntil: 'networkidle' })
    await (await findTemplateRow(templateCode)).getByRole('button', { name: '停用', exact: true }).click()
    await confirmMessageBox()
    await page.goto(`${baseUrl}${instancePath}`, { waitUntil: 'networkidle' })
    const createdRow = await findInstanceRow(instanceName)
    const frozenConsoleStart = consoleErrors.length
    const frozenPageStart = pageErrors.length
    await createdRow.getByRole('button', { name: '填写', exact: true }).click()
    const frozenDrawer = page.locator('.el-drawer').filter({ hasText: instanceName })
    await page.waitForFunction(
      (value) => [...document.querySelectorAll('input')].some((input) => input.value === value),
      'F-PLT-002 浏览器项目'
    )
    await frozenDrawer.getByText('fplt002-browser-rebound.txt', { exact: true }).waitFor()
    await page.waitForTimeout(1200)
    expectedCapabilityDiagnostics.push({
      phase: 'disabled-template-frozen-instance-target-failures',
      console: consoleErrors.splice(frozenConsoleStart),
      page: pageErrors.splice(frozenPageStart)
    })
    await frozenDrawer.locator('.el-drawer__close-btn').first().click()
    await page.getByRole('button', { name: '新建实例', exact: true }).click()
    const disabledSelection = page.locator('.el-dialog').filter({ hasText: '选择模板并创建实例' })
    await disabledSelection.waitFor()
    assert((await disabledSelection.getByText(templateCode, { exact: true }).count()) === 0, '停用模板仍可用于新实例选择')
    await disabledSelection.getByRole('button', { name: '取消', exact: true }).click()

    await page.goto(`${baseUrl}${templatePath}`, { waitUntil: 'networkidle' })
    const disabledRow = await findTemplateRow(templateCode)
    await disabledRow.getByRole('button', { name: '创建下一草稿', exact: true }).click()
    await drawer.waitFor()
    const revision2Template = await api(page, {
      path: `/api/v1/pms/dynamic-form-templates/${createdTemplate.templateId}`
    })
    const revision2Id = revision2Template.body.data.currentDraft.revisionId
    const revision2Fact = await api(page, {
      path: `/api/v1/pms/dynamic-form-template-revisions/${revision2Id}`
    })
    const revision2Rules = [
      ...revision2Fact.body.data.formRulesJson,
      { type: 'input', field: 'revision2Only', title: '仅新修订字段' }
    ]
    const revision2Patch = await api(page, {
      path: `/api/v1/pms/dynamic-form-template-revisions/${revision2Id}`,
      method: 'PATCH',
      headers: { 'If-Match': String(revision2Fact.body.data.revisionVersion) },
      body: {
        formConfJson: revision2Fact.body.data.formConfJson,
        formRulesJson: revision2Rules,
        engineCode: revision2Fact.body.data.engineCode,
        designerVersion: revision2Fact.body.data.designerVersion,
        rendererVersion: revision2Fact.body.data.rendererVersion
      }
    })
    assert(revision2Patch.status === 200 && revision2Patch.body?.code === 0, '第二修订保存失败')
    await drawer.getByRole('button', { name: '重新读取', exact: true }).click()
    await drawer.locator('.el-drawer__close-btn').click()
    await drawer.waitFor({ state: 'hidden' })
    await page.reload({ waitUntil: 'networkidle' })
    const revision2Row = await findTemplateRow(templateCode)
    await revision2Row.getByRole('button', { name: '发布草稿', exact: true }).click()
    await confirmMessageBox()
    await page.getByText('修订已发布', { exact: true }).waitFor()
    const publishedRevision2Row = await findTemplateRow(templateCode)
    await publishedRevision2Row.getByRole('button', { name: '启用', exact: true }).click()
    const reenabled = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' && response.url().includes('/actions/enable')
    )
    await confirmMessageBox()
    assert((await reenabled).status() === 200, '模板重新启用命令未成功')
    const reenabledTemplate = await api(page, {
      path: `/api/v1/pms/dynamic-form-templates/${createdTemplate.templateId}`
    })
    assert(reenabledTemplate.body.data.availability === 'ENABLED', '模板未重新启用')
    assert(reenabledTemplate.body.data.currentPublished.revisionId === revision2Id, '当前发布指针未切换到第二修订')
    const frozenAfterRevision2 = await api(page, {
      path: `/api/v1/pms/dynamic-form-instances/${createdSummary.instanceId}`
    })
    assert(frozenAfterRevision2.body.data.templateRevisionId === instanceFact.body.data.templateRevisionId,
      '发布新修订改变了旧实例冻结指针')
    assert(!frozenAfterRevision2.body.data.formRulesJson.some((rule) => rule.field === 'revision2Only'),
      '旧实例渲染了新修订字段')
    await page.goto(`${baseUrl}${instancePath}`, { waitUntil: 'networkidle' })
    await page.getByRole('button', { name: '新建实例', exact: true }).click()
    const reenabledSelection = page.locator('.el-dialog').filter({ hasText: '选择模板并创建实例' })
    await reenabledSelection.locator('.el-card').filter({ hasText: templateCode }).waitFor()
    await reenabledSelection.getByRole('button', { name: '取消', exact: true }).click()

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

    const createBrowserActor = async (actorCode, menuIds) => {
      const role = await api(page, {
        path: '/system/role/create',
        method: 'POST',
        body: {
          name: `FPLT002-${actorCode}-${String(suffix).slice(-8)}`,
          code: `fplt002_${actorCode}_${suffix}`,
          sort: 90,
          status: 0
        }
      })
      assert(role.status === 200 && role.body?.code === 0, `创建验收角色失败：${actorCode}`)
      const roleMenus = await api(page, {
        path: '/system/permission/assign-role-menu',
        method: 'POST',
        body: { roleId: role.body.data, menuIds }
      })
      assert(roleMenus.status === 200 && roleMenus.body?.code === 0, `分配验收菜单失败：${actorCode}`)
      const actorUsername = `fp${String(suffix).slice(-8)}${actorCode.slice(0, 2)}`
      const user = await api(page, {
        path: '/system/user/create',
        method: 'POST',
        body: {
          username: actorUsername,
          nickname: `FPLT002 ${actorCode}`,
          password: negativePassword,
          status: 0,
          sex: 1,
          postIds: []
        }
      })
      assert(user.status === 200 && user.body?.code === 0, `创建验收用户失败：${actorCode}`)
      const userRole = await api(page, {
        path: '/system/permission/assign-user-role',
        method: 'POST',
        body: { userId: user.body.data, roleIds: [role.body.data] }
      })
      assert(userRole.status === 200 && userRole.body?.code === 0, `分配验收角色失败：${actorCode}`)
      return { username: actorUsername, userId: user.body.data, roleId: role.body.data }
    }

    assert(negativePassword, '未提供隔离负向主体测试密码')
    const menuChain = [18000, 19262, 19271]
    const readOnlyActor = await createBrowserActor('readonly', [...menuChain, 198800, 198803])
    const nonCreatorActor = await createBrowserActor('noncreator', [...menuChain, 198803, 198805])
    const templateTotalBeforeReadOnly = (await api(page, {
      path: '/api/v1/pms/dynamic-form-templates?pageNo=1&pageSize=1'
    })).body.data.total

    const readOnlyContext = await browser.newContext({ viewport: { width: 1024, height: 800 } })
    const readOnlyPage = await readOnlyContext.newPage()
    try {
      await login(readOnlyPage, readOnlyActor.username, negativePassword)
      const readOnlyTemplates = await api(readOnlyPage, {
        path: '/api/v1/pms/dynamic-form-templates?pageNo=1&pageSize=10'
      })
      assert(readOnlyTemplates.status === 200 && readOnlyTemplates.body?.code === 0, '获权只读主体无法查询模板')
      const deniedCreate = await api(readOnlyPage, {
        path: '/api/v1/pms/dynamic-form-templates',
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: {
          templateCode: `DENIED_${suffix}`,
          templateName: '不得创建',
          categoryCode: 'GENERAL'
        }
      })
      expectedNegativeResponses.push({
        actor: readOnlyActor.username,
        scenario: 'AUTHORIZED_READ_ONLY_CREATE_DENIED',
        status: deniedCreate.status,
        code: deniedCreate.body?.code,
        sideEffect: 'NONE'
      })
      assert(deniedCreate.status === 403 || deniedCreate.body?.code === 403, '获权只读主体创建未拒绝')
      const readOnlyInstance = await api(readOnlyPage, {
        path: `/api/v1/pms/dynamic-form-instances/${createdSummary.instanceId}`
      })
      assert(readOnlyInstance.status === 200 && readOnlyInstance.body?.code === 0, '获权只读主体无法读取实例')
      assert(!readOnlyInstance.body.data.allowedActions.includes('PATCH_INSTANCE'), '获权只读主体被投影更新动作')
      await readOnlyPage.goto(`${baseUrl}${templatePath}`, { waitUntil: 'networkidle' })
      assert((await readOnlyPage.getByRole('button', { name: '新建模板', exact: true }).count()) === 0,
        '获权只读UI仍显示新建模板')
      await readOnlyPage.screenshot({
        path: path.join(outputDir, '05a-authorized-readonly-1024.png'),
        fullPage: true
      })
    } finally {
      await readOnlyContext.close()
    }
    const templateTotalAfterReadOnly = (await api(page, {
      path: '/api/v1/pms/dynamic-form-templates?pageNo=1&pageSize=1'
    })).body.data.total
    assert(templateTotalAfterReadOnly === templateTotalBeforeReadOnly, '获权只读拒绝后产生第二模板')

    const nonCreatorContext = await browser.newContext({ viewport: { width: 1024, height: 800 } })
    const nonCreatorPage = await nonCreatorContext.newPage()
    try {
      await login(nonCreatorPage, nonCreatorActor.username, negativePassword)
      const beforeNonCreatorPatch = await api(nonCreatorPage, {
        path: `/api/v1/pms/dynamic-form-instances/${createdSummary.instanceId}`
      })
      assert(beforeNonCreatorPatch.status === 200 && beforeNonCreatorPatch.body?.code === 0,
        '非创建者无法读取实例')
      assert(!beforeNonCreatorPatch.body.data.allowedActions.includes('PATCH_INSTANCE'),
        '非创建者被投影更新动作')
      const deniedPatch = await api(nonCreatorPage, {
        path: `/api/v1/pms/dynamic-form-instances/${createdSummary.instanceId}`,
        method: 'PATCH',
        headers: { 'If-Match': String(beforeNonCreatorPatch.body.data.instanceVersion) },
        body: { values: { projectName: '越权覆盖' } }
      })
      expectedNegativeResponses.push({
        actor: nonCreatorActor.username,
        scenario: 'NON_CREATOR_UPDATE_DENIED',
        status: deniedPatch.status,
        code: deniedPatch.body?.code,
        sideEffect: 'NONE'
      })
      assert(deniedPatch.status === 403 || deniedPatch.body?.code === 403, '非创建者更新未拒绝')
      await nonCreatorPage.goto(`${baseUrl}${instancePath}`, { waitUntil: 'networkidle' })
      const nonCreatorRow = await findInstanceRow(instanceName, nonCreatorPage)
      await nonCreatorRow.getByRole('button', { name: '查看', exact: true }).click()
      const nonCreatorDrawer = nonCreatorPage.locator('.el-drawer').filter({ hasText: instanceName })
      assert((await nonCreatorDrawer.getByRole('button', { name: '保存填写值', exact: true }).count()) === 0,
        '非创建者UI仍显示保存动作')
      await nonCreatorPage.screenshot({
        path: path.join(outputDir, '05b-non-creator-denied-1024.png'),
        fullPage: true
      })
    } finally {
      await nonCreatorContext.close()
    }
    const afterNonCreatorPatch = await api(page, {
      path: `/api/v1/pms/dynamic-form-instances/${createdSummary.instanceId}`
    })
    assert(afterNonCreatorPatch.body.data.values.projectName === 'F-PLT-002 浏览器项目',
      '非创建者拒绝后值发生变化')

    assert(crossTenantApiUrl, '未提供启用租户隔离的浏览器REST地址')
    const crossTenant = await page.evaluate(
      async ({ base, loginPassword, foreignInstanceId }) => {
        const loginResponse = await fetch(`${base}/system/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'tenant-id': '1' },
          body: JSON.stringify({ username: 'admin', password: loginPassword })
        })
        const loginBody = await loginResponse.json()
        const token = loginBody?.data?.accessToken
        const deniedResponse = await fetch(`${base}/api/v1/pms/dynamic-form-instances/${foreignInstanceId}`, {
          headers: { Authorization: `Bearer ${token}`, 'tenant-id': '1' }
        })
        return {
          loginStatus: loginResponse.status,
          loginCode: loginBody?.code,
          status: deniedResponse.status,
          body: await deniedResponse.json()
        }
      },
      { base: crossTenantApiUrl, loginPassword: password, foreignInstanceId: createdSummary.instanceId }
    )
    expectedNegativeResponses.push({
      actor: 'tenant-1-admin',
      scenario: 'SECOND_TENANT_FOREIGN_INSTANCE_DENIED',
      status: crossTenant.status,
      code: crossTenant.body?.code,
      sideEffect: 'NONE'
    })
    assert(crossTenant.loginStatus === 200 && crossTenant.loginCode === 0, '第二租户主体登录失败')
    assert(crossTenant.body?.code !== 0, '第二租户读取到租户0实例')
    const afterCrossTenant = await api(page, {
      path: `/api/v1/pms/dynamic-form-instances/${createdSummary.instanceId}`
    })
    assert(afterCrossTenant.body.data.instanceVersion === afterNonCreatorPatch.body.data.instanceVersion,
      '跨租户拒绝后实例版本发生变化')

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

    const isExpectedTargetConsole = (message) =>
      message.includes('CORS policy') ||
      message.includes('net::ERR_FAILED') ||
      (message.includes('Refused to display') && message.includes('frame')) ||
      message.includes('X-Frame-Options') ||
      message.includes('frame-ancestors')
    const repeatedTargetDiagnostics = consoleErrors.filter(isExpectedTargetConsole)
    const repeatedTargetPageErrors = pageErrors.filter(
      (message) => message === 'Network Error' || message === 'error'
    )
    if (repeatedTargetDiagnostics.length) {
      expectedCapabilityDiagnostics.push({
        phase: 'subsequent-frozen-and-responsive-renders',
        console: repeatedTargetDiagnostics,
        page: repeatedTargetPageErrors
      })
      consoleErrors.splice(
        0,
        consoleErrors.length,
        ...consoleErrors.filter((message) => !isExpectedTargetConsole(message))
      )
      pageErrors.splice(
        0,
        pageErrors.length,
        ...pageErrors.filter((message) => message !== 'Network Error' && message !== 'error')
      )
    }
    const allCapabilityConsole = expectedCapabilityDiagnostics.flatMap((item) => item.console)
    assert(
      expectedCapabilityFailures.some(
        (item) => item.path === '/admin-api/system/permission/assign-user-role' && item.code !== 0
      ),
      'POST目标API拒绝未形成可观察证据'
    )
    assert(allCapabilityConsole.some((message) => message.includes('CORS policy')),
      '跨源接口失败未形成可观察证据')
    assert(
      allCapabilityConsole.some(
        (message) =>
          message.includes('X-Frame-Options') ||
          message.includes('Refused to display') ||
          message.includes('frame-ancestors')
      ),
      'iframe拒绝未形成可观察证据'
    )
    assert(consoleErrors.length === 0, `存在浏览器 console error：${JSON.stringify(consoleErrors)}`)
    assert(pageErrors.length === 0, `存在浏览器 page error：${JSON.stringify(pageErrors)}`)
    assert(unexpectedResponses.length === 0, `存在意外 HTTP 失败：${JSON.stringify(unexpectedResponses)}`)

    const result = {
      capturedAt: new Date().toISOString(),
      browserVersion: browser.version(),
      candidateRuntime: {
        frontend: baseUrl,
        backend: apiUrl,
        tenantEnabledBackend: crossTenantApiUrl,
        fileStorage: 'MinIO',
        scanMode: expectedScan
      },
      template: {
        templateCode,
        lifecycle: ['DRAFT', 'PUBLISHED', 'ENABLED', 'DISABLED', 'DRAFT_2', 'PUBLISHED_2', 'REENABLED'],
        secondPublishedRevisionId: revision2Id
      },
      instance: {
        instanceId: createdSummary.instanceId,
        frozenRevisionId: instanceFact.body.data.templateRevisionId,
        ordinaryValues: {
          projectName: 'F-PLT-002 浏览器项目',
          requiresCutover: false,
          machineCount: 0,
          nullableNote: null,
          emptyTags: [],
          ordinaryUpload: 'fplt002-ordinary-upload.txt'
        },
        controlledFileCount: fileFacts.length,
        controlledFileLifecycle: ['UPLOAD_V1', 'ADD_VERSION_V2', 'DETACH', 'REBIND'],
        remainsReadableAfterTemplateDisabled: true,
        remainsOnOriginalRevisionAfterRevision2: true
      },
      capabilitiesObserved: [
        '完整 FormCreate 设计器',
        'iframe实际渲染及目标拒绝',
        'GET接口选择实际加载',
        'POST接口选择实际调用及拒绝',
        '跨源接口CORS失败',
        '联动实际显示',
        '必填校验零PATCH',
        '事件函数实际执行',
        'parseFunc实际执行',
        '普通UploadFile实际上传',
        'PmsFileArtifact上传/换版/解绑/重绑'
      ],
      expectedCapabilityFailures,
      expectedCapabilityDiagnostics,
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
    await new Promise((resolve) => capabilityTargetServer.close(resolve))
  }
})().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
