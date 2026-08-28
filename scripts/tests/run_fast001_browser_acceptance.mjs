import fs from 'node:fs'
import path from 'node:path'

const endpoint = process.argv[2] || 'http://127.0.0.1:9223'
const appUrl = process.argv[3] || 'http://127.0.0.1:18083'
const apiUrl = process.argv[4] || 'http://127.0.0.1:58082/admin-api'
const outputDir = process.argv[5] || 'output/f-ast-001/browser'
const password = process.env.FAST001_BROWSER_PASSWORD
if (!password) throw new Error('必须通过 FAST001_BROWSER_PASSWORD 环境变量显式提供验收密码')
const roles = ['fast001_browser_operator', 'fast001_browser_readonly', 'fast001_browser_denied']
const users = {
  operator: 'fast001operator',
  readonly: 'fast001readonly',
  denied: 'fast001denied'
}
const fixtures = {
  mainId: '970000000000000001',
  configurationLogId: '970000000000071001',
  mainSn: 'FAST001_SN_MAIN',
  staleSn: 'FAST001_SN_CHILD_1',
  failedSn: 'FAST001_SN_CHILD_2',
  unavailableSn: 'FAST001_SN_NOT_AVAILABLE',
  customerId: '970000000000002002',
  customerCode: 'FAST001_CUSTOMER_SUMMARY',
  emptyCustomerId: '970000000000002099'
}
const tabLabels = ['出厂信息', '官网信息', '在网版本', '技术公告', '维保信息', '配置Log']
const viewports = [
  ['320', 320, 740],
  ['768', 768, 900],
  ['1024', 1024, 800],
  ['1440', 1440, 900]
]

fs.mkdirSync(outputDir, { recursive: true })
const page = await fetch(`${endpoint}/json/new?about:blank`, { method: 'PUT' }).then((response) => response.json())
if (!page?.webSocketDebuggerUrl) throw new Error('无法创建独立Chrome验收页签')
const socket = new WebSocket(page.webSocketDebuggerUrl)
await new Promise((resolve, reject) => {
  socket.addEventListener('open', resolve, { once: true })
  socket.addEventListener('error', reject, { once: true })
})

let sequence = 0
const pending = new Map()
const consoleErrors = []
const failedApiResponses = []
socket.addEventListener('message', (event) => {
  const message = JSON.parse(event.data)
  if (message.id && pending.has(message.id)) {
    const { resolve, reject } = pending.get(message.id)
    pending.delete(message.id)
    return message.error ? reject(new Error(message.error.message)) : resolve(message.result)
  }
  if (message.method === 'Runtime.exceptionThrown') {
    const details = message.params.exceptionDetails
    consoleErrors.push(details.exception?.description || details.text)
  }
  if (message.method === 'Log.entryAdded' && message.params.entry.level === 'error') {
    consoleErrors.push(message.params.entry.text)
  }
  if (message.method === 'Network.responseReceived') {
    const response = message.params.response
    if (response.url.includes('/admin-api/') && response.status >= 400) {
      failedApiResponses.push({ status: response.status, url: response.url })
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
  await wait(2500)
}
const clickText = async (text) => evaluate(`(() => {
  const element = [...document.querySelectorAll('button,a,[role="tab"]')]
    .find((node) => node.textContent.trim().includes(${JSON.stringify(text)}));
  if (!element) return false;
  element.click();
  return true;
})()`)
const setInput = async (label, value) => evaluate(`(() => {
  const item = [...document.querySelectorAll('.el-form-item')]
    .find((node) => node.textContent.includes(${JSON.stringify(label)}));
  const input = item?.querySelector('input');
  if (!input) return false;
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
  setter.call(input, ${JSON.stringify(value)});
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
  return true;
})()`)
const screenshot = async (name) => {
  const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  fs.writeFileSync(path.join(outputDir, `${name}.png`), Buffer.from(image.data, 'base64'))
}
const rawApi = async (method, url, data, headers = {}, token) => evaluate(`(async () => {
  const response = await fetch(${JSON.stringify(apiUrl)} + ${JSON.stringify(url)}, {
    method: ${JSON.stringify(method)},
    headers: {
      'tenant-id': '1',
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
const api = async (method, url, data, headers = {}, token) => {
  const response = await rawApi(method, url, data, headers, token)
  if (response.status >= 400 || response.body?.code !== 0) {
    throw new Error(`${method} ${url} 失败：HTTP ${response.status}, code=${response.body?.code}, msg=${response.body?.msg}`)
  }
  return response.body.data
}
const login = async (username) => {
  const response = await rawApi('POST', '/system/auth/login', {
    username,
    password,
    captchaVerification: ''
  })
  if (response.status >= 400 || response.body?.code !== 0 || !response.body?.data?.accessToken) {
    throw new Error(`${username} 登录失败：${JSON.stringify(response)}`)
  }
  return response.body.data.accessToken
}
const bodyIncludes = (text) => evaluate(`document.body.innerText.includes(${JSON.stringify(text)})`)

await Promise.all([
  send('Page.enable'),
  send('Runtime.enable'),
  send('Network.enable'),
  send('Log.enable')
])
await navigate(appUrl)
const operatorToken = await login(users.operator)
const readonlyToken = await login(users.readonly)
const deniedToken = await login(users.denied)

const apiChecks = {}
const mainPage = await api('GET', `/pms/asset/devices/page?pageNo=1&pageSize=10&sn=${fixtures.mainSn}`, undefined, {}, operatorToken)
apiChecks.snExact = mainPage.total === 1 && mainPage.list?.[0]?.sn === fixtures.mainSn
apiChecks.thinList = mainPage.list?.[0]?.packageNo === 'FAST001_PACKAGE_CURRENT'
  && mainPage.list?.[0]?.contractNo === 'FAST001_CONTRACT_CURRENT'
  && mainPage.list?.[0]?.conpVersion === 'FAST001_CONP_EXACT_1.2.3'
const stalePage = await api('GET', `/pms/asset/devices/page?pageNo=1&pageSize=10&sn=${fixtures.staleSn}`, undefined, {}, operatorToken)
const failedPage = await api('GET', `/pms/asset/devices/page?pageNo=1&pageSize=10&sn=${fixtures.failedSn}`, undefined, {}, operatorToken)
const unavailablePage = await api('GET', `/pms/asset/devices/page?pageNo=1&pageSize=10&sn=${fixtures.unavailableSn}`, undefined, {}, operatorToken)
apiChecks.sourceStatuses = stalePage.list?.[0]?.syncStatus === 'STALE'
  && failedPage.list?.[0]?.syncStatus === 'FAILED'
  && unavailablePage.list?.[0]?.syncStatus === 'NOT_AVAILABLE'
const detail = await api('GET', `/pms/asset/devices/${fixtures.mainId}`, undefined, {}, operatorToken)
apiChecks.conpCombination = detail.networkVersion?.data?.conpVersion === 'FAST001_CONP_EXACT_1.2.3'
  && detail.networkVersion?.data?.conpType === 'FAST001_CONP_TYPE_A'
  && detail.networkVersion?.data?.conpSeries === 'FAST001_CONP_SERIES_A'
  && detail.networkVersion?.data?.conpMark === '1.2.3'
apiChecks.notAvailableSlices = ['factory', 'official', 'technicalNotice', 'warranty', 'configurationLog']
  .every((key) => detail[key]?.syncStatus === 'NOT_AVAILABLE')
const projectHistory = await api('GET', `/pms/asset/devices/${fixtures.mainId}/assignment-history?pageNo=1&pageSize=20`, undefined, {}, operatorToken)
const customerRelationships = await api('GET', `/pms/asset/devices/${fixtures.mainId}/customer-relationships?pageNo=1&pageSize=20`, undefined, {}, operatorToken)
const assembly = await api('GET', `/pms/asset/devices/${fixtures.mainId}/assembly-tree`, undefined, {}, operatorToken)
const warranty = await api('GET', `/pms/asset/devices/${fixtures.mainId}/warranty-records?pageNo=1&pageSize=20`, undefined, {}, operatorToken)
apiChecks.relationships = projectHistory.total > 0 && customerRelationships.total > 0
apiChecks.assemblyDepth = assembly.some((item) => item.parentDeviceSn === fixtures.mainSn && item.childDeviceSn === fixtures.staleSn)
  && assembly.some((item) => item.parentDeviceSn === fixtures.staleSn && item.childDeviceSn === fixtures.failedSn)
apiChecks.warranty = warranty.current?.warrantyMonths === 12 && warranty.records?.total > 0
const readonlyAssign = await rawApi('POST', `/pms/asset/devices/${fixtures.mainId}/actions/assign-project`, {
  projectId: detail.summary.projectId,
  reason: 'FAST001_BROWSER_READONLY_NEGATIVE'
}, { 'If-Match': String(detail.summary.projectAssignmentVersion), 'Idempotency-Key': crypto.randomUUID() }, readonlyToken)
apiChecks.readonlyAssignDenied = readonlyAssign.body?.code !== 0
const staleAssign = await rawApi('POST', `/pms/asset/devices/${fixtures.mainId}/actions/assign-project`, {
  projectId: detail.summary.projectId,
  reason: 'FAST001_BROWSER_STALE_VERSION'
}, { 'If-Match': '0', 'Idempotency-Key': crypto.randomUUID() }, operatorToken)
apiChecks.staleVersionRejected = staleAssign.body?.code !== 0
const deniedQuery = await rawApi('GET', `/pms/asset/devices/page?pageNo=1&pageSize=10&sn=${fixtures.mainSn}`, undefined, {}, deniedToken)
apiChecks.deniedQuery = deniedQuery.body?.code !== 0
const readonlyLogs = await api('GET', `/pms/asset/devices/${fixtures.mainId}/configuration-logs`, undefined, {}, readonlyToken)
apiChecks.readonlyDownloadHidden = readonlyLogs.every((item) => !item.downloadable)
const deniedDownloadGrant = await rawApi('POST', `/pms/asset/devices/${fixtures.mainId}/configuration-logs/${fixtures.configurationLogId}/download-url`, undefined, {}, readonlyToken)
apiChecks.downloadGrantDenied = deniedDownloadGrant.body?.code !== 0
const legacyPage = await api('GET', '/pms/equipment/page?pageNo=1&pageSize=10', undefined, {}, readonlyToken)
const legacyWrite = await rawApi('POST', '/pms/equipment/create', {
  serialNumber: `FAST001_BROWSER_DENIED_${Date.now()}`,
  name: 'FAST001 browser denied legacy write'
}, {}, readonlyToken)
apiChecks.legacyReadonly = Array.isArray(legacyPage.list) && legacyWrite.body?.code !== 0
const legacyMarker = `FAST001_BROWSER_SUPER_${Date.now()}`
const adminToken = await login('admin')
const customerDetail = await api('GET', `/pms/customers/${fixtures.customerId}`, undefined, {}, adminToken)
const customerDeviceCodes = customerDetail.devices?.items?.map((item) => item.deviceCode) || []
apiChecks.customerSummaryCurrentProjection = customerDetail.devices?.available === true
  && customerDeviceCodes.includes(fixtures.mainSn)
apiChecks.customerSummaryEffectiveRelationships = customerDeviceCodes.includes(fixtures.staleSn)
  && customerDeviceCodes.includes(fixtures.failedSn)
apiChecks.customerSummaryDeduplicated = customerDetail.devices?.total === 3
  && customerDeviceCodes.filter((code) => code === fixtures.mainSn).length === 1
  && !customerDeviceCodes.includes(fixtures.unavailableSn)
const emptyCustomerDetail = await api('GET', `/pms/customers/${fixtures.emptyCustomerId}`, undefined, {}, adminToken)
apiChecks.customerSummaryEmptyPage = emptyCustomerDetail.devices?.available === true
  && emptyCustomerDetail.devices?.total === 0
  && emptyCustomerDetail.devices?.items?.length === 0
const beforeAst = await api('GET', `/pms/asset/devices/page?pageNo=1&pageSize=10&sn=${legacyMarker}`, undefined, {}, adminToken)
const createdLegacyId = await api('POST', '/pms/equipment/create', {
  serialNumber: legacyMarker,
  name: 'FAST001 browser super admin regression'
}, {}, adminToken)
const afterAst = await api('GET', `/pms/asset/devices/page?pageNo=1&pageSize=10&sn=${legacyMarker}`, undefined, {}, adminToken)
apiChecks.superAdminLegacyWrite = Number.isFinite(createdLegacyId) && beforeAst.total === 0 && afterAst.total === 0
await api('DELETE', `/pms/equipment/delete?id=${createdLegacyId}`, undefined, {}, adminToken)

await navigate(`${appUrl}/customer-asset/devices`)
await evaluate(`localStorage.clear()`)
await navigate(`${appUrl}/login`)
const loginFilled = await evaluate(`(() => {
  const inputs = [...document.querySelectorAll('input')];
  const username = inputs.find((input) => /账号|用户名/.test(input.placeholder || '')) || inputs.find((input) => input.type === 'text');
  const passwordInput = inputs.find((input) => input.type === 'password');
  if (!username || !passwordInput) return false;
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
  setter.call(username, 'admin');
  username.dispatchEvent(new Event('input', { bubbles: true }));
  setter.call(passwordInput, ${JSON.stringify(password)});
  passwordInput.dispatchEvent(new Event('input', { bubbles: true }));
  return true;
})()`)
if (!loginFilled || !(await clickText('登录'))) throw new Error('操作角色登录表单不可操作')
await wait(4000)
await navigate(`${appUrl}/customer-asset/devices`)
const uiChecks = {}
uiChecks.routeLoaded = await bodyIncludes('设备SN')
uiChecks.queryInput = await setInput('设备SN', fixtures.mainSn)
uiChecks.queryAction = await clickText('查询')
await wait(1800)
uiChecks.queryResult = await bodyIncludes(fixtures.mainSn)
uiChecks.detailAction = await clickText('详情')
await wait(1800)
uiChecks.detailLoaded = await bodyIncludes('FAST001 test product A')
uiChecks.sixTabs = (await Promise.all(tabLabels.map((label) => bodyIncludes(label)))).every(Boolean)
const tabChecks = {}
for (const label of tabLabels) {
  tabChecks[label] = await clickText(label)
  await wait(500)
}
uiChecks.tabsClickable = Object.values(tabChecks).every(Boolean)
await clickText('在网版本')
await wait(500)
uiChecks.conpVisible = await bodyIncludes('FAST001_CONP_EXACT_1.2.3')
await clickText('维保信息')
await wait(500)
uiChecks.warrantyLoad = await clickText('加载维保记录')
await wait(1200)
uiChecks.warrantyVisible = await bodyIncludes('FAST001_WARRANTY_CONTRACT')
uiChecks.assemblyDrawer = await clickText('装配树')
await wait(1200)
uiChecks.assemblyVisible = await bodyIncludes(fixtures.staleSn)
await evaluate(`document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))`)
await wait(400)
uiChecks.assignmentDrawer = await clickText('项目历史')
await wait(1000)
uiChecks.assignmentVisible = await bodyIncludes('FAST001_ASSIGNMENT_MISMATCH')
await evaluate(`document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))`)
await wait(400)
uiChecks.customerDrawer = await clickText('客户关系')
await wait(1000)
uiChecks.customerVisible = await bodyIncludes('FAST001_ASSIGNMENT_MISMATCH')
await evaluate(`document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))`)
await wait(400)
await send('Page.reload', { ignoreCache: true })
await wait(2500)
uiChecks.refreshPersistent = await bodyIncludes(fixtures.mainSn)

await navigate(`${appUrl}/customer-asset/customers`)
await wait(2000)
uiChecks.customerRouteLoaded = await bodyIncludes('客户编码')
uiChecks.customerQueryInput = await setInput('客户编码', fixtures.customerCode)
uiChecks.customerQueryAction = await clickText('查询')
await wait(1500)
uiChecks.customerQueryResult = await bodyIncludes(fixtures.customerCode)
uiChecks.customerDetailAction = await clickText('详情')
await wait(1500)
uiChecks.customerDetailLoaded = await bodyIncludes('FAST001客户设备摘要验收')
uiChecks.customerDeviceTab = await clickText('设备摘要')
await wait(700)
uiChecks.customerCurrentProjectionVisible = await bodyIncludes(fixtures.mainSn)
uiChecks.customerEffectiveLeaseVisible = await bodyIncludes(fixtures.staleSn)
uiChecks.customerEffectiveCoManagedVisible = await bodyIncludes(fixtures.failedSn)
uiChecks.customerExpiredHistoryHidden = !(await bodyIncludes(fixtures.unavailableSn))
const customerDeviceRows = await evaluate(`[
  ...document.querySelectorAll('.el-table__body-wrapper tbody tr')
].map((row) => row.innerText)`)
uiChecks.customerSummaryDeduplicated = customerDeviceRows.filter((row) => row.includes(fixtures.mainSn)).length === 1
await clickText('项目摘要')
await wait(500)
uiChecks.customerOtherSliceAvailable = !(await bodyIncludes('Owner 摘要暂不可用'))

await clickText('设备摘要')
await wait(300)
const responsive = {}
for (const [name, width, height] of viewports) {
  await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width <= 768 })
  await wait(700)
  responsive[name] = await evaluate(`({
    viewport: [innerWidth, innerHeight],
    overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    pageVisible: document.body.innerText.includes('设备摘要')
  })`)
  await screenshot(name)
}

const expectedNegativeFragments = [
  '/actions/assign-project',
  '/configuration-logs/970000000000071001/download-url',
  '/pms/equipment/create',
  '/pms/asset/devices/page'
]
const unexplainedFailedApiResponses = failedApiResponses.filter((failure) =>
  !expectedNegativeFragments.some((fragment) => failure.url.includes(fragment)))
const result = {
  fixtures,
  roles,
  users,
  apiChecks,
  uiChecks,
  tabChecks,
  responsive,
  consoleErrors,
  failedApiResponses,
  unexplainedFailedApiResponses,
  pass: Object.values(apiChecks).every(Boolean)
    && Object.values(uiChecks).every(Boolean)
    && Object.values(responsive).every((item) => item.pageVisible && !item.overflow)
    && consoleErrors.length === 0
    && unexplainedFailedApiResponses.length === 0
}
fs.writeFileSync(path.join(outputDir, 'result.json'), `${JSON.stringify(result, null, 2)}\n`)
console.log(JSON.stringify(result, null, 2))
await send('Browser.close')
if (!result.pass) process.exitCode = 1
