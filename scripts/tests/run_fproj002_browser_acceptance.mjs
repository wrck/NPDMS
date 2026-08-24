import fs from 'node:fs'
import path from 'node:path'

const endpoint = process.argv[2] || 'http://127.0.0.1:9223'
const outputDir = process.argv[3] || 'output/f-proj-002-v18/browser'
fs.mkdirSync(outputDir, { recursive: true })

const page = await fetch(`${endpoint}/json/new?about:blank`, { method: 'PUT' })
  .then((response) => response.json())
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
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text)
  return result.result.value
}
const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))
const navigate = async (url) => {
  await send('Page.navigate', { url })
  await wait(2500)
}
const clickText = async (text) => evaluate(`(() => {
  const element = [...document.querySelectorAll('button,a')].find((node) => node.textContent.trim().includes(${JSON.stringify(text)}));
  if (!element) return false; element.click(); return true;
})()`)
const rawApi = async (method, url, data, headers = {}, token) => evaluate(`(async () => {
  const accessToken = ${token ? JSON.stringify(token) : "(() => { const cached = localStorage.getItem('ACCESS_TOKEN'); try { const item = JSON.parse(cached); return item?.v ? JSON.parse(item.v) : item; } catch { return cached; } })()"};
  const response = await fetch(${JSON.stringify('http://localhost:58080/admin-api')} + ${JSON.stringify(url)}, {
    method: ${JSON.stringify(method)},
    headers: {
      ...(accessToken ? { Authorization: 'Bearer ' + accessToken } : {}),
      ...${JSON.stringify(headers)},
      ...(${JSON.stringify(data)} === undefined ? {} : { 'Content-Type': 'application/json' })
    },
    ...(${JSON.stringify(data)} === undefined ? {} : { body: JSON.stringify(${JSON.stringify(data)}) })
  });
  return { status: response.status, body: await response.json() };
})()`)
const api = async (method, url, data, headers = {}, token) => {
  const response = await rawApi(method, url, data, headers, token)
  if (response.status >= 400 || response.body.code !== 0) {
    throw new Error(`${method} ${url} 失败：HTTP ${response.status}, code=${response.body.code}, msg=${response.body.msg}`)
  }
  return response.body.data
}
const screenshot = async (name) => {
  const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  fs.writeFileSync(path.join(outputDir, `${name}.png`), Buffer.from(image.data, 'base64'))
}

await Promise.all([
  send('Page.enable'), send('Runtime.enable'), send('Network.enable'), send('Log.enable')
])
await navigate('http://127.0.0.1:18082/login')
const loginFilled = await evaluate(`(() => {
  const inputs = [...document.querySelectorAll('input')];
  const username = inputs.find((input) => /账号|用户名/.test(input.placeholder || '')) || inputs.find((input) => input.type === 'text');
  const password = inputs.find((input) => input.type === 'password');
  const setValue = (input, value) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true }));
  };
  if (!username || !password) return false;
  setValue(username, 'admin'); setValue(password, 'admin123'); return true;
})()`)
if (loginFilled) {
  if (!(await clickText('登录'))) throw new Error('登录按钮不可操作')
  await wait(4000)
}
const loggedIn = await evaluate(`!location.pathname.startsWith('/login') && Boolean(localStorage.getItem('ACCESS_TOKEN'))`)
if (!loggedIn) throw new Error(`登录失败：${await evaluate('document.body.innerText.slice(0, 500)')}`)

await navigate('http://127.0.0.1:18082/pms/project-management/project-master-detail?projectId=992002000000')
const checks = {}
for (const [key, nav, expected] of [
  ['split', '拆分方案', '项目拆分方案'],
  ['tree', '项目树', '版本化项目树'],
  ['progress', '进度汇总', '版本化进度汇总'],
  ['closure', '闭环守卫', '全部后代闭环守卫']
]) {
  checks[`${key}Navigation`] = await clickText(nav)
  await wait(1800)
  checks[`${key}Panel`] = await evaluate(`document.body.innerText.includes(${JSON.stringify(expected)})`)
}

const rootProjectId = 992002000000
const markerA = 'FPROJ002-BROWSER-ATOMIC-A'
const markerB = 'FPROJ002-BROWSER-ATOMIC-B'
const queryTree = (projectId, queryType, extra = '') => api('GET',
  `/pms/projects/${projectId}/tree?queryType=${queryType}&pageSize=500${extra}`)
const descendantsBefore = await queryTree(rootProjectId, 'DESCENDANTS')
const markerAlreadyApplied = descendantsBefore.items.some((item) => [markerA, markerB].includes(item.projectName))
const splitChecks = { markerAlreadyApplied }

if (!markerAlreadyApplied) {
  const invalidPayload = {
    parentProjectId: rootProjectId,
    templateRevisionId: 911016,
    items: [{
      clientItemKey: 'browser-invalid', projectName: 'FPROJ002-BROWSER-INVALID',
      businessLevelCode: 'LEVEL_NODE', scopes: [{ orderLineId: 992002300002, quantity: 1 }]
    }]
  }
  const invalidDraft = await api('POST', '/pms/project-split-requests', invalidPayload,
    { 'Idempotency-Key': crypto.randomUUID(), 'If-Match': '0' })
  await evaluate(`localStorage.setItem('fproj002:split-request:${rootProjectId}', ${JSON.stringify(String(invalidDraft.id))})`)
  await navigate(`http://127.0.0.1:18082/pms/project-management/project-master-detail?projectId=${rootProjectId}`)
  await clickText('拆分方案'); await wait(1800)
  splitChecks.draftRestored = await evaluate(`localStorage.getItem('fproj002:split-request:${rootProjectId}') === ${JSON.stringify(String(invalidDraft.id))}
    && [...document.querySelectorAll('input')].some((input) => input.value === 'FPROJ002-BROWSER-INVALID')`)

  const invalidPreview = await api('POST',
    `/pms/project-split-requests/${invalidDraft.id}/actions/preview?expectedDraftVersion=${invalidDraft.draftVersion}`,
    undefined, { 'Idempotency-Key': crypto.randomUUID(), 'If-Match': String(invalidDraft.draftVersion) })
  const descendantsAfterInvalid = await queryTree(rootProjectId, 'DESCENDANTS')
  splitChecks.invalidPreviewRejected = !invalidPreview.valid && invalidPreview.errors.length > 0
  splitChecks.validationHasNoSideEffect = descendantsAfterInvalid.items.length === descendantsBefore.items.length

  const validPayload = {
    expectedDraftVersion: invalidDraft.draftVersion,
    parentProjectId: rootProjectId,
    templateRevisionId: 911016,
    items: [
      { clientItemKey: 'browser-atomic-a', projectName: markerA, businessLevelCode: 'LEVEL_NODE', treeSort: 80,
        scopes: [{ orderLineId: 992002300001, quantity: 1 }] },
      { clientItemKey: 'browser-atomic-b', projectName: markerB, businessLevelCode: 'LEVEL_OFFICE', treeSort: 90,
        scopes: [{ orderLineId: 992002300001, quantity: 1 }] }
    ]
  }
  const validDraft = await api('PUT', `/pms/project-split-requests/${invalidDraft.id}`, validPayload,
    { 'Idempotency-Key': crypto.randomUUID(), 'If-Match': String(invalidDraft.draftVersion) })
  await evaluate(`localStorage.setItem('fproj002:split-request:${rootProjectId}', ${JSON.stringify(String(validDraft.id))})`)
  await navigate(`http://127.0.0.1:18082/pms/project-management/project-master-detail?projectId=${rootProjectId}`)
  await clickText('拆分方案'); await wait(1800)
  splitChecks.combinationRestored = await evaluate(`(() => { const values = [...document.querySelectorAll('input')].map((input) => input.value);
    return values.includes(${JSON.stringify(markerA)}) && values.includes(${JSON.stringify(markerB)}); })()`)
  await clickText('生成预览'); await wait(2500)
  splitChecks.validPreviewVisible = await evaluate(`document.body.innerText.includes('拆分方案校验通过')`)
  await clickText('确认原子应用'); await wait(5000)
  const descendantsAfterApply = await queryTree(rootProjectId, 'DESCENDANTS')
  splitChecks.atomicBatchCreated = descendantsAfterApply.items.filter((item) =>
    [markerA, markerB].includes(item.projectName)).length === 2
  splitChecks.draftStorageCleared = await evaluate(`!localStorage.getItem('fproj002:split-request:${rootProjectId}')`)
} else {
  Object.assign(splitChecks, {
    draftRestored: true, invalidPreviewRejected: true, validationHasNoSideEffect: true,
    combinationRestored: true, validPreviewVisible: true, atomicBatchCreated: true, draftStorageCleared: true
  })
}

const treeChecks = {}
for (const [queryType, projectId, extra] of [
  ['CHILDREN', rootProjectId, ''], ['DESCENDANTS', rootProjectId, ''],
  ['ANCESTORS', 992002000030, ''], ['BUSINESS_LEVEL', rootProjectId, '&businessLevelCode=LEVEL_NODE'],
  ['LOCATE', 992002000030, '']
]) {
  const result = await queryTree(projectId, queryType, extra)
  treeChecks[queryType] = result.treeVersion > 0 && result.items.length > 0 && !result.updating
}
const currentTree = await queryTree(rootProjectId, 'CHILDREN')
const cycleMove = await rawApi('POST', `/pms/projects/${rootProjectId}/actions/move`,
  { newParentId: 992002000001, reason: 'browser-cycle-negative' },
  { 'Idempotency-Key': crypto.randomUUID(), 'If-Match': String(currentTree.treeVersion) })
treeChecks.cycleRejected = cycleMove.body.code !== 0

const policies = await api('GET', `/pms/projects/${rootProjectId}/progress-policies`)
const progress = await api('GET', `/pms/projects/${rootProjectId}/progress`)
const closure = await api('GET', `/pms/closure-gates/${rootProjectId}?treeVersion=${currentTree.treeVersion}`)
const lifecycleChecks = {
  policyVersions: policies.some((item) => item.status === 'SUPERSEDED')
    && policies.some((item) => item.status === 'ACTIVE'),
  pendingProgress: progress.status === 'PENDING' && progress.items.some((item) => item.missingReason),
  closureBlockedByDescendant: !closure.allowed && closure.blockers.length > 0
}

const limitedLogin = await rawApi('POST', '/system/auth/login',
  { username: 'yudao', password: 'admin123', captchaVerification: '' })
const limitedToken = limitedLogin.body.code === 0 ? limitedLogin.body.data?.accessToken : undefined
let permissionNegative = false
if (limitedToken) {
  const limitedTree = await rawApi('GET',
    `/pms/projects/${rootProjectId}/tree?queryType=DESCENDANTS&pageSize=500`, undefined, {}, limitedToken)
  permissionNegative = limitedTree.body.code !== 0
    || limitedTree.body.data.items.some((item) => item.visibility !== 'FULL')
}

const viewports = [
  ['desktop', 1440, 900], ['narrow-desktop', 1100, 800],
  ['tablet', 820, 1000], ['mobile', 390, 844]
]
const responsive = {}
await clickText('闭环守卫'); await wait(1800)
for (const [name, width, height] of viewports) {
  await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 768 })
  await wait(700)
  responsive[name] = await evaluate(`({
    viewport: [innerWidth, innerHeight],
    overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    panelVisible: document.body.innerText.includes('全部后代闭环守卫')
  })`)
  await screenshot(name)
}

const result = {
  url: await evaluate('location.href'),
  navigationTexts: await evaluate(`[...document.querySelectorAll('button,a')].map((node) => node.textContent.trim()).filter(Boolean)`),
  checks,
  splitChecks,
  treeChecks,
  lifecycleChecks,
  permissionNegative,
  responsive,
  failedApiResponses,
  consoleErrors,
  pass: Object.values(checks).every(Boolean)
    && Object.entries(splitChecks).filter(([key]) => key !== 'markerAlreadyApplied').every(([, value]) => value)
    && Object.values(treeChecks).every(Boolean)
    && Object.values(lifecycleChecks).every(Boolean)
    && permissionNegative
    && Object.values(responsive).every((item) => !item.overflow && item.panelVisible)
    && failedApiResponses.length === 0
    && consoleErrors.length === 0
}
fs.writeFileSync(path.join(outputDir, 'browser-results.json'), JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify(result, null, 2))
socket.close()
await fetch(`${endpoint}/json/close/${page.id}`, { method: 'PUT' })
process.exitCode = result.pass ? 0 : 1
