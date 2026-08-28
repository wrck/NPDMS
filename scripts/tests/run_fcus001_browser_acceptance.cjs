const fs = require('node:fs')
const path = require('node:path')

const endpoint = process.argv[2] || 'http://127.0.0.1:9224'
const appUrl = process.argv[3] || 'http://127.0.0.1:19081'
const apiUrl = process.argv[4] || 'http://127.0.0.1:59280/admin-api'
const outputDir = process.argv[5] || 'output/f-cus-001-v18/browser-current'
const password = process.env.FCUS001_BROWSER_PASSWORD
if (!password) throw new Error('必须通过 FCUS001_BROWSER_PASSWORD 环境变量显式提供验收密码')

const users = {
  operator: { username: 'fcus001operator', tenantId: 1 },
  readonly: { username: 'fcus001readonly', tenantId: 1 },
  denied: { username: 'fcus001denied', tenantId: 1 },
  crossTenant: { username: 'fcus001tenant121', tenantId: 970000000000090000 }
}
const classification = {
  departmentCode: 'FCUS001_OFFICE',
  marketCode: 'FCUS001_MARKET',
  systemCode: 'FCUS001_SYSTEM',
  expendCode: 'FCUS001_EXPEND',
  industryCode: 'FCUS001_INDUSTRY'
}
const viewports = [
  ['320', 320, 740],
  ['768', 768, 900],
  ['1024', 1024, 800],
  ['1440', 1440, 900]
]

;(async () => {
  fs.mkdirSync(path.join(outputDir, 'screenshots'), { recursive: true })
  const target = await fetch(`${endpoint}/json/new?about:blank`, { method: 'PUT' }).then((response) => response.json())
  if (!target?.webSocketDebuggerUrl) throw new Error('无法创建独立 Chrome 验收页签')
  const socket = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true })
    socket.addEventListener('error', reject, { once: true })
  })

  let sequence = 0
  const pending = new Map()
  const consoleErrors = []
  const pageErrors = []
  const failedResponses = []
  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    if (message.id && pending.has(message.id)) {
      const callback = pending.get(message.id)
      pending.delete(message.id)
      return message.error ? callback.reject(new Error(message.error.message)) : callback.resolve(message.result)
    }
    if (message.method === 'Runtime.exceptionThrown') {
      const details = message.params.exceptionDetails
      pageErrors.push(details.exception?.description || details.text)
    }
    if (message.method === 'Log.entryAdded' && message.params.entry.level === 'error') {
      consoleErrors.push(message.params.entry.text)
    }
    if (message.method === 'Network.responseReceived') {
      const response = message.params.response
      if (response.url.includes('/admin-api/') && response.status >= 400) {
        failedResponses.push({ status: response.status, url: response.url })
      }
    }
  })
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence
    pending.set(id, { resolve, reject })
    socket.send(JSON.stringify({ id, method, params }))
  })
  const evaluate = async (expression) => {
    const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)
    return result.result.value
  }
  const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))
  const navigate = async (url) => {
    await send('Page.navigate', { url })
    await wait(2200)
  }
  const clickText = (text) => evaluate(`(() => {
    const nodes = [...document.querySelectorAll('button,a,[role="tab"],[role="option"],li')];
    const visible = nodes.filter((node) => node.offsetParent !== null);
    const element = visible.find((node) => node.textContent.trim() === ${JSON.stringify(text)})
      || visible.find((node) => node.textContent.trim().includes(${JSON.stringify(text)}));
    if (!element) return false;
    element.click();
    return true;
  })()`)
  const clickAt = async (point) => {
    if (!point) return false
    await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: point.x, y: point.y, button: 'left', clickCount: 1 })
    await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: point.x, y: point.y, button: 'left', clickCount: 1 })
    return true
  }
  const clickFormSelect = async (label) => clickAt(await evaluate(`(() => {
    const root = [...document.querySelectorAll('.el-drawer')].find((node) => node.getClientRects().length > 0) || document;
    const item = [...root.querySelectorAll('.el-form-item')]
      .find((node) => node.textContent.includes(${JSON.stringify(label)}));
    const element = item?.querySelector('.el-select__wrapper,.el-select');
    if (!element) return null;
    const rect = element.getBoundingClientRect();
    return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
  })()`))
  const clickSelectOption = async (text) => clickAt(await evaluate(`(() => {
    const option = [...document.querySelectorAll('.el-select-dropdown__item,[role="option"]')]
      .find((node) => node.getClientRects().length > 0 && node.textContent.trim() === ${JSON.stringify(text)});
    if (!option) return null;
    const rect = option.getBoundingClientRect();
    return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
  })()`))
  const setControl = (label, value) => evaluate(`(() => {
    const root = [...document.querySelectorAll('.el-drawer')].find((node) => node.offsetParent !== null) || document;
    const item = [...root.querySelectorAll('.el-form-item')]
      .find((node) => node.textContent.includes(${JSON.stringify(label)}));
    const input = item?.querySelector('input,textarea');
    if (!input) return false;
    const prototype = input.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(prototype, 'value').set.call(input, ${JSON.stringify(value)});
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  })()`)
  const bodyIncludes = (text) => evaluate(`document.body.innerText.includes(${JSON.stringify(text)})`)
  const screenshot = async (name) => {
    const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    fs.writeFileSync(path.join(outputDir, 'screenshots', `${name}.png`), Buffer.from(image.data, 'base64'))
  }
  const rawApi = (method, url, data, headers = {}, token, tenantId = 1) => evaluate(`(async () => {
    const response = await fetch(${JSON.stringify(apiUrl)} + ${JSON.stringify(url)}, {
      method: ${JSON.stringify(method)},
      headers: {
        'tenant-id': ${JSON.stringify(String(tenantId))},
        ...${JSON.stringify(headers)},
        ...(${token ? JSON.stringify(token) : 'null'} ? { authorization: 'Bearer ' + ${token ? JSON.stringify(token) : 'null'} } : {}),
        ...(${JSON.stringify(data)} === undefined ? {} : { 'Content-Type': 'application/json' })
      },
      ...(${JSON.stringify(data)} === undefined ? {} : { body: JSON.stringify(${JSON.stringify(data)}) })
    });
    const text = await response.text();
    let body;
    try { body = JSON.parse(text); } catch { body = text; }
    return { status: response.status, body };
  })()`)
  const api = async (method, url, data, headers = {}, token, tenantId = 1) => {
    const response = await rawApi(method, url, data, headers, token, tenantId)
    if (response.status >= 400 || response.body?.code !== 0) {
      throw new Error(`${method} ${url} 失败：HTTP ${response.status}, code=${response.body?.code}, msg=${response.body?.msg}`)
    }
    return response.body.data
  }
  const login = async ({ username, tenantId }) => {
    const response = await rawApi('POST', '/system/auth/login', {
      username,
      password,
      captchaVerification: ''
    }, {}, undefined, tenantId)
    if (response.status >= 400 || response.body?.code !== 0 || !response.body?.data?.accessToken) {
      throw new Error(`${username} 登录失败：${JSON.stringify(response)}`)
    }
    return response.body.data.accessToken
  }
  const uiLogin = async (username) => {
    await navigate(`${appUrl}/login`)
    await evaluate('localStorage.clear()')
    await send('Page.reload', { ignoreCache: true })
    await wait(1500)
    const filled = await evaluate(`(() => {
      const inputs = [...document.querySelectorAll('input')];
      const textInputs = inputs.filter((input) => input.type === 'text');
      const tenant = textInputs.find((input) => /租户/.test(input.placeholder || ''));
      const user = textInputs.find((input) => /账号|用户名/.test(input.placeholder || '')) || textInputs.at(-1);
      const pass = inputs.find((input) => input.type === 'password');
      if (!user || !pass) return false;
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
      if (tenant) {
        setter.call(tenant, 'NPMS默认租户');
        tenant.dispatchEvent(new Event('input', { bubbles: true }));
        tenant.dispatchEvent(new Event('change', { bubbles: true }));
      }
      setter.call(user, ${JSON.stringify(username)}); user.dispatchEvent(new Event('input', { bubbles: true })); user.dispatchEvent(new Event('change', { bubbles: true }));
      setter.call(pass, ${JSON.stringify(password)}); pass.dispatchEvent(new Event('input', { bubbles: true })); pass.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    })()`)
    if (!filled || !(await clickText('登录'))) throw new Error(`${username} 登录表单不可操作`)
    await wait(3500)
    if (await evaluate(`location.pathname.includes('/login')`)) {
      const diagnostic = await evaluate(`({
        inputs: [...document.querySelectorAll('input')].map((input) => ({
          placeholder: input.placeholder, type: input.type, value: input.value
        })),
        text: document.body.innerText.slice(-800)
      })`)
      throw new Error(`${username} UI 登录未离开登录页：${JSON.stringify(diagnostic)}`)
    }
  }

  await Promise.all([
    send('Page.enable'), send('Runtime.enable'), send('Network.enable'), send('Log.enable')
  ])
  await navigate(appUrl)
  const operatorToken = await login(users.operator)
  const readonlyToken = await login(users.readonly)
  const deniedToken = await login(users.denied)
  const crossTenantToken = await login(users.crossTenant)
  const adminToken = await login({ username: 'admin', tenantId: 1 })

  const marker = Date.now()
  const platformCode = `FCUS001_BROWSER_${marker}`
  const temporaryCode = `FCUS001_TEMP_${marker}`
  const platformPayload = {
    code: platformCode,
    name: `F-CUS-001 浏览器平台客户 ${marker}`,
    shortName: 'CUS平台验收',
    remark: 'F-CUS-001 browser acceptance',
    sourceType: 'PLATFORM_CREATED',
    reconciliationPending: false,
    ...classification
  }
  const createKey = crypto.randomUUID()
  const firstCreate = await api('POST', '/pms/customers', platformPayload,
    { 'Idempotency-Key': createKey }, operatorToken)
  const replayCreate = await api('POST', '/pms/customers', platformPayload,
    { 'Idempotency-Key': createKey }, operatorToken)
  const platformId = firstCreate.customerId
  let platformDetail = await api('GET', `/pms/customers/${platformId}`, undefined, {}, operatorToken)
  const update = await api('PUT', `/pms/customers/${platformId}`, {
    remark: 'F-CUS-001 browser updated',
    changedFields: ['remark']
  }, { 'If-Match': String(platformDetail.version), 'Idempotency-Key': crypto.randomUUID() }, operatorToken)
  platformDetail = await api('GET', `/pms/customers/${platformId}`, undefined, {}, operatorToken)

  await uiLogin(users.operator.username)
  await navigate(`${appUrl}/customer-asset/customers`)
  const uiChecks = {}
  uiChecks.routeLoaded = await bodyIncludes('客户编码')
  uiChecks.createDrawerOpened = await clickText('创建客户')
  await wait(500)
  uiChecks.crmSourceHidden = !(await bodyIncludes('CRM 同步'))
  uiChecks.sourceSelectOpened = await clickFormSelect('来源类型')
  await wait(300)
  uiChecks.temporaryOptionClicked = uiChecks.sourceSelectOpened && await clickSelectOption('平台临时')
  await wait(300)
  uiChecks.temporarySelected = await bodyIncludes('临时客户原因')
  await wait(300)
  const formValues = [
    ['客户编码', temporaryCode],
    ['客户名称', `F-CUS-001 浏览器临时客户 ${marker}`],
    ['临时客户原因', '现场交付先行，等待 CRM 对账'],
    ['办事处编码', classification.departmentCode],
    ['市场部编码', classification.marketCode],
    ['系统部编码', classification.systemCode],
    ['拓展部编码', classification.expendCode],
    ['子行业编码', classification.industryCode]
  ]
  uiChecks.temporaryFormFilled = (await Promise.all(formValues.map(([label, value]) => setControl(label, value)))).every(Boolean)
  uiChecks.temporarySaved = await clickText('保存')
  await wait(1800)
  const temporaryPage = await api('GET', `/pms/customers?pageNo=1&pageSize=10&code=${temporaryCode}`, undefined, {}, operatorToken)
  const temporaryId = temporaryPage.list?.[0]?.id
  if (!temporaryId) {
    const visibleText = await evaluate('document.body.innerText.slice(-1200)')
    throw new Error(`临时客户未落库：saved=${uiChecks.temporarySaved}, page=${JSON.stringify(temporaryPage)}, `
      + `failed=${JSON.stringify(failedResponses)}, pageText=${visibleText}`)
  }
  let temporaryDetail = await api('GET', `/pms/customers/${temporaryId}`, undefined, {}, operatorToken)
  uiChecks.temporaryPersisted = temporaryDetail.sourceType === 'PLATFORM_TEMPORARY'
    && temporaryDetail.reconciliationPending === true
    && temporaryDetail.temporaryReason === '现场交付先行，等待 CRM 对账'
  await setControl('客户编码', temporaryCode)
  await clickText('查询')
  await wait(1200)
  uiChecks.temporaryVisible = await bodyIncludes(temporaryCode)
  await clickText('详情')
  await wait(1000)
  uiChecks.temporaryReasonVisible = await bodyIncludes('现场交付先行，等待 CRM 对账')
    && await bodyIncludes('待对账')

  const readonlyCreate = await rawApi('POST', '/pms/customers', {
    ...platformPayload,
    code: `${platformCode}_READONLY`
  }, { 'Idempotency-Key': crypto.randomUUID() }, readonlyToken)
  const deniedQuery = await rawApi('GET', '/pms/customers?pageNo=1&pageSize=10', undefined, {}, deniedToken)
  const crossTenantPage = await api('GET', `/pms/customers?pageNo=1&pageSize=10&code=${platformCode}`,
    undefined, {}, crossTenantToken, users.crossTenant.tenantId)
  const referenced = await api('GET', '/pms/customers/970000000000002002', undefined, {}, operatorToken)
  const guardedDelete = await rawApi('POST', '/pms/customers/970000000000002002/actions/delete',
    { reason: 'F-CUS-001 referenced delete negative' },
    { 'If-Match': String(referenced.version), 'Idempotency-Key': crypto.randomUUID() }, operatorToken)

  const deleteResult = await api('POST', `/pms/customers/${temporaryId}/actions/delete`,
    { reason: 'F-CUS-001 browser delete' },
    { 'If-Match': String(temporaryDetail.version), 'Idempotency-Key': crypto.randomUUID() }, operatorToken)
  const deletedPage = await api('GET', `/pms/customers?pageNo=1&pageSize=10&code=${temporaryCode}&lifecycleStatus=DELETED`,
    undefined, {}, operatorToken)
  const defaultAfterDelete = await api('GET', `/pms/customers?pageNo=1&pageSize=10&code=${temporaryCode}`,
    undefined, {}, operatorToken)
  const restoreResult = await api('POST', `/pms/customers/${temporaryId}/actions/restore`,
    { reason: 'F-CUS-001 browser restore' },
    { 'If-Match': String(deleteResult.version), 'Idempotency-Key': crypto.randomUUID() }, operatorToken)
  temporaryDetail = await api('GET', `/pms/customers/${temporaryId}`, undefined, {}, operatorToken)

  await uiLogin(users.readonly.username)
  await navigate(`${appUrl}/customer-asset/customers`)
  uiChecks.readonlyRouteLoaded = await bodyIncludes('客户编码')
  uiChecks.readonlyCreateHidden = !(await bodyIncludes('创建客户'))
  await setControl('客户编码', platformCode)
  await clickText('查询')
  await wait(900)
  uiChecks.readonlyMutationHidden = !(await bodyIncludes('编辑'))
    && !(await bodyIncludes('停用')) && !(await bodyIncludes('删除')) && !(await bodyIncludes('恢复'))

  await uiLogin(users.operator.username)
  await navigate(`${appUrl}/customer-asset/customers`)
  await setControl('客户编码', temporaryCode)
  await clickText('查询')
  await wait(900)
  await clickText('详情')
  await wait(700)
  const responsive = {}
  for (const [name, width, height] of viewports) {
    await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width <= 768 })
    await wait(500)
    responsive[name] = await evaluate(`({
      viewport: [innerWidth, innerHeight],
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
      pageVisible: document.body.innerText.includes('客户编码'),
      detailVisible: document.body.innerText.includes('待对账')
    })`)
    await screenshot(`customer-${name}`)
  }

  const adminPage = await api('GET', `/pms/customers?pageNo=1&pageSize=10&code=${platformCode}`, undefined, {}, adminToken)
  const apiChecks = {
    platformCreated: platformDetail.id === platformId && platformDetail.remark === 'F-CUS-001 browser updated',
    idempotentReplay: firstCreate.customerId === replayCreate.customerId && replayCreate.replayed === true,
    idempotentSingleFact: (await api('GET', `/pms/customers?pageNo=1&pageSize=10&code=${platformCode}`, undefined, {}, operatorToken)).total === 1,
    temporaryPersisted: uiChecks.temporaryPersisted,
    deletedFilter: deletedPage.total === 1 && deletedPage.list?.[0]?.id === temporaryId && defaultAfterDelete.total === 0,
    restoredIdentity: restoreResult.customerId === temporaryId && temporaryDetail.id === temporaryId
      && temporaryDetail.code === temporaryCode && temporaryDetail.lifecycleStatus === 'ENABLED',
    readonlyDenied: readonlyCreate.body?.code !== 0,
    deniedQuery: deniedQuery.body?.code !== 0,
    crossTenantIsolated: crossTenantPage.total === 0,
    referencedDeleteBlocked: guardedDelete.body?.code === 1014001004,
    superAdminPreserved: adminPage.total === 1
  }
  const unexpectedResponses = failedResponses
  const result = {
    generatedAt: new Date().toISOString(),
    ports: { chrome: 9224, backend: 59280, frontend: 19081, mysql: 23316, redis: 26379 },
    users,
    created: { platformId, platformCode, temporaryId, temporaryCode },
    lifecycle: { deleteVersion: deleteResult.version, restoreVersion: restoreResult.version },
    apiChecks,
    uiChecks,
    responsive,
    consoleErrors,
    pageErrors,
    failedResponses,
    guardedDelete,
    unexpectedResponses,
    pass: Object.values(apiChecks).every(Boolean)
      && Object.values(uiChecks).every(Boolean)
      && Object.values(responsive).every((item) => item.pageVisible && item.detailVisible && !item.overflow)
      && consoleErrors.length === 0 && pageErrors.length === 0 && unexpectedResponses.length === 0
  }
  fs.writeFileSync(path.join(outputDir, 'result.json'), `${JSON.stringify(result, null, 2)}\n`)
  console.log(JSON.stringify(result, null, 2))
  await send('Browser.close')
  if (!result.pass) process.exitCode = 1
})().catch((error) => {
  console.error(error)
  process.exit(1)
})
