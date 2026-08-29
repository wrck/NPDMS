const fs = require('node:fs')
const path = require('node:path')
const { execFileSync } = require('node:child_process')

const endpoint = process.argv[2] || 'http://127.0.0.1:9224'
const appUrl = process.argv[3] || 'http://127.0.0.1:19081'
const apiUrl = process.argv[4] || 'http://127.0.0.1:59280/admin-api'
const outputDir = process.argv[5] || 'docs/engineering/evidence/f-com-001-browser'
const password = process.env.FCOM001_BROWSER_PASSWORD
const adminPassword = process.env.FCOM001_ADMIN_PASSWORD
if (!password) throw new Error('必须通过 FCOM001_BROWSER_PASSWORD 环境变量提供验收密码')
if (!adminPassword) throw new Error('必须通过 FCOM001_ADMIN_PASSWORD 环境变量提供管理员密码')

const tenantId = 0
const managedUser = { id: 992002800002, username: 'fcom001acceptance' }
const managedRoleId = 992002800001
const managedMenus = [19260, 930900, 930901, 930902, 930903, 930904, 930905, 930906, 930907, 18069]
const rootProject = { id: 992002000000, version: 0, scopeVersion: 1 }
const stageProject = { id: 992002900001, version: 0, scopeVersion: 1, treeVersion: 1 }
const viewports = [
  ['320', 320, 740], ['768', 768, 900], ['1024', 1024, 800], ['1440', 1440, 900]
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
  const networkFailures = []
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
    if (message.method === 'Network.loadingFailed') {
      networkFailures.push({ url: message.params.requestId, errorText: message.params.errorText })
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
  const screenshot = async (name) => {
    const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    fs.writeFileSync(path.join(outputDir, 'screenshots', `${name}.png`), Buffer.from(image.data, 'base64'))
  }
  const setInput = (label, value) => evaluate(`(() => {
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
  const clickText = (text) => evaluate(`(() => {
    const element = [...document.querySelectorAll('button,a')]
      .filter((node) => node.offsetParent !== null)
      .find((node) => node.textContent.trim().includes(${JSON.stringify(text)}));
    if (!element) return false;
    element.click();
    return true;
  })()`)
  const bodyIncludes = (text) => evaluate(`document.body.innerText.includes(${JSON.stringify(text)})`)

  const rawApi = (method, url, data, headers = {}, token) => evaluate(`(async () => {
    const response = await fetch(${JSON.stringify(apiUrl)} + ${JSON.stringify(url)}, {
      method: ${JSON.stringify(method)},
      headers: {
        'tenant-id': '0',
        ...${JSON.stringify(headers)},
        ...(${token ? `{ authorization: 'Bearer ' + ${JSON.stringify(token)} }` : '{}'}),
        ...(${data === undefined ? '{}' : "{ 'Content-Type': 'application/json' }"})
      },
      ...(${data === undefined ? '{}' : `{ body: JSON.stringify(${JSON.stringify(data)}) }`})
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
  const expectDenied = async (name, method, url, data, headers, token, expectedCode) => {
    const response = await rawApi(method, url, data, headers, token)
    return { name, status: response.status, code: response.body?.code, message: response.body?.msg,
      expectedCode, denied: response.body?.code === expectedCode }
  }
  const login = async (username, loginPassword) => {
    const response = await rawApi('POST', '/system/auth/login', {
      username, password: loginPassword, captchaVerification: ''
    })
    if (response.status >= 400 || response.body?.code !== 0 || !response.body?.data?.accessToken) {
      throw new Error(`${username} 登录失败：HTTP ${response.status}, code=${response.body?.code}, msg=${response.body?.msg}`)
    }
    return response.body.data.accessToken
  }
  const uiLogin = async (username) => {
    await send('Storage.clearDataForOrigin', {
      origin: new URL(appUrl).origin,
      storageTypes: 'all'
    })
    await navigate(`${appUrl}/login`)
    const filled = await evaluate(`(() => {
      const inputs = [...document.querySelectorAll('input')];
      const texts = inputs.filter((input) => input.type === 'text');
      const user = texts.find((input) => /账号|用户名/.test(input.placeholder || '')) || texts.at(-1);
      const pass = inputs.find((input) => input.type === 'password');
      if (!user || !pass) return false;
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
      setter.call(user, ${JSON.stringify(username)});
      user.dispatchEvent(new Event('input', { bubbles: true }));
      user.dispatchEvent(new Event('change', { bubbles: true }));
      setter.call(pass, ${JSON.stringify(password)});
      pass.dispatchEvent(new Event('input', { bubbles: true }));
      pass.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    })()`)
    if (!filled || !(await clickText('登录'))) throw new Error('公开登录表单不可操作')
    await wait(3500)
    if (await evaluate(`location.pathname.includes('/login')`)) throw new Error('正式验收身份未离开登录页')
  }
  const mysql = (sql) => execFileSync('docker', [
    'exec', 'npdms-50eb-test-mysql-1', 'sh', '-c',
    'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" npdms_test -e "$1"', '_', sql
  ], { encoding: 'utf8' }).trim()

  await Promise.all([send('Page.enable'), send('Runtime.enable'), send('Network.enable'), send('Log.enable')])
  await navigate(appUrl)

  const adminToken = await login('admin', adminPassword)
  await api('PUT', '/system/user/update-password', { id: managedUser.id, password }, {}, adminToken)
  const fullToken = await login(managedUser.username, password)
  const marker = Date.now()
  const suffix = String(marker).slice(-9)

  const seedSensitive = (await api('GET', '/api/v1/pms/contracts/992002390001', undefined, {}, fullToken)).contract
  const seededFiltered = await api('GET',
    '/api/v1/pms/contracts?companyCode=DPTECH-DEMO&contractType=SALES&sourceSystem=SEED&status=ENABLED&pageNo=1&pageSize=20',
    undefined, {}, fullToken)
  await api('POST', '/system/permission/assign-role-menu', {
    roleId: managedRoleId, menuIds: managedMenus.filter((id) => id !== 930902)
  }, {}, adminToken)
  const seedMasked = (await api('GET', '/api/v1/pms/contracts/992002390001', undefined, {}, fullToken)).contract
  await api('POST', '/system/permission/assign-role-menu', { roleId: managedRoleId, menuIds: managedMenus }, {}, adminToken)

  const contractKey = `FCOM001-CONTRACT-${marker}`
  const orderKey = `FCOM001-ORDER-${marker}`
  const lineKeys = ['A', 'B', 'C'].map((name) => `FCOM001-LINE-${name}-${marker}`)
  const sourceTime = Date.parse('2026-08-29T22:00:00+08:00')
  const importPayload = {
    sourceBatchId: `FCOM001-BATCH-${marker}`,
    contracts: [{ sourceRecordKey: contractKey, sourceVersion: '1', companyCode: 'DPTECH-DEMO',
      contractNo: `FCOM-${suffix}`, contractName: `F-COM-001 浏览器合同 ${suffix}`,
      status: 'ENABLED', sourceUpdatedAt: sourceTime }],
    salesOrders: [{ sourceRecordKey: orderKey, sourceVersion: '1', companyCode: 'DPTECH-DEMO',
      orderType: 'NORMAL', orderNo: `SO-FCOM-${suffix}`, status: 'ENABLED', sourceUpdatedAt: sourceTime }],
    salesOrderLines: [
      { sourceRecordKey: lineKeys[0], sourceVersion: '1', orderSourceRecordKey: orderKey, lineNo: '10',
        itemCode: 'ITEM-A', itemDescription: '浏览器范围调整', productCode: 'PRODUCT-A', orderQuantity: 50,
        openQuantity: 50, deliveredQuantity: 0, unitCode: 'SET', unitScale: 0,
        quantityStatus: 'CONFIRMED', status: 'ENABLED', sourceUpdatedAt: sourceTime },
      { sourceRecordKey: lineKeys[1], sourceVersion: '1', orderSourceRecordKey: orderKey, lineNo: '20',
        itemCode: 'ITEM-B', itemDescription: '浏览器释放与阶段内绑定', productCode: 'PRODUCT-B', orderQuantity: 20,
        openQuantity: 20, deliveredQuantity: 0, unitCode: 'SET', unitScale: 0,
        quantityStatus: 'CONFIRMED', status: 'ENABLED', sourceUpdatedAt: sourceTime },
      { sourceRecordKey: lineKeys[2], sourceVersion: '1', orderSourceRecordKey: orderKey, lineNo: '30',
        itemCode: 'ITEM-C', itemDescription: '浏览器AST负向', productCode: 'PRODUCT-C', orderQuantity: 10,
        openQuantity: 10, deliveredQuantity: 0, unitCode: 'SET', unitScale: 0,
        quantityStatus: 'CONFIRMED', status: 'ENABLED', sourceUpdatedAt: sourceTime }
    ]
  }
  await api('POST', '/system/permission/assign-role-menu', {
    roleId: managedRoleId, menuIds: managedMenus.filter((id) => id !== 930907)
  }, {}, adminToken)
  const noAuthority = await expectDenied('无Authority写权限', 'POST',
    '/api/v1/pms/commerce-authority/import-batches', importPayload,
    { 'Idempotency-Key': crypto.randomUUID(), 'X-Source-System': 'ERP' }, fullToken, 403)
  await api('POST', '/system/permission/assign-role-menu', { roleId: managedRoleId, menuIds: managedMenus }, {}, adminToken)
  const importKey = crypto.randomUUID()
  const imported = await api('POST', '/api/v1/pms/commerce-authority/import-batches', importPayload,
    { 'Idempotency-Key': importKey, 'X-Source-System': 'ERP' }, fullToken)
  const importReplay = await api('POST', '/api/v1/pms/commerce-authority/import-batches', importPayload,
    { 'Idempotency-Key': importKey, 'X-Source-System': 'ERP' }, fullToken)
  const changedImport = structuredClone(importPayload)
  changedImport.contracts[0].contractName += ' changed'
  const importConflict = await expectDenied('同幂等键异载荷', 'POST',
    '/api/v1/pms/commerce-authority/import-batches', changedImport,
    { 'Idempotency-Key': importKey, 'X-Source-System': 'ERP' }, fullToken, 1010002000)
  const contracts = await api('GET', `/api/v1/pms/contracts?companyCode=DPTECH-DEMO&contractNo=FCOM-${suffix}&sourceSystem=ERP&status=ENABLED&pageNo=1&pageSize=20`,
    undefined, {}, fullToken)
  const contract = contracts.list[0]
  if (!contract) throw new Error('受控导入合同未出现在公开查询')
  await api('POST', `/api/v1/pms/contracts/${contract.id}/project-relations`, {
    projectId: rootProject.id, relationRole: 'RELATED', reason: 'F-COM-001 Chromium acceptance'
  }, { 'Idempotency-Key': crypto.randomUUID() }, fullToken)
  const contractDetail = await api('GET', `/api/v1/pms/contracts/${contract.id}`, undefined, {}, fullToken)
  await api('POST', '/api/v1/pms/contracts/992002390001/project-relations', {
    projectId: rootProject.id, relationRole: 'RELATED', reason: 'F-COM-001 detail aggregate acceptance'
  }, { 'Idempotency-Key': crypto.randomUUID() }, fullToken)
  const seedDetail = await api('GET', '/api/v1/pms/contracts/992002390001', undefined, {}, fullToken)
  const orders = await api('GET', `/api/v1/pms/sales-orders?companyCode=DPTECH-DEMO&orderNo=SO-FCOM-${suffix}&orderType=NORMAL&status=ENABLED&pageNo=1&pageSize=20`,
    undefined, {}, fullToken)
  const order = orders.list[0]
  const lines = await api('GET', `/api/v1/pms/order-lines?companyCode=DPTECH-DEMO&orderType=NORMAL&orderNo=${encodeURIComponent(order.orderNo)}&quantityStatus=CONFIRMED&status=ENABLED&pageNo=1&pageSize=20`,
    undefined, {}, fullToken)
  const lineA = lines.list.find((line) => line.lineNo === '10')
  const lineB = lines.list.find((line) => line.lineNo === '20')
  const lineC = lines.list.find((line) => line.lineNo === '30')

  const previewA = await api('POST', '/api/v1/pms/delivery-scopes/actions/preview', {
    projectId: stageProject.id, expectedProjectVersion: stageProject.version,
    expectedProjectScopeVersion: stageProject.scopeVersion, orderLineId: lineA.id,
    expectedOrderLineSourceVersion: lineA.sourceVersion, proposedQuantity: 10, serialNumbers: []
  }, {}, fullToken)
  const assignedA = await api('POST', '/api/v1/pms/delivery-scopes/actions/assign', {
    projectId: stageProject.id, expectedProjectScopeVersion: stageProject.scopeVersion, orderLineId: lineA.id,
    expectedOrderLineSourceVersion: lineA.sourceVersion, allocatedQuantity: 10, serialNumbers: [],
    reason: 'F-COM-001 Chromium assign A'
  }, { 'If-Match': String(stageProject.version), 'Idempotency-Key': crypto.randomUUID() }, fullToken)
  const adjustedA = await api('POST', `/api/v1/pms/delivery-scopes/${assignedA.deliveryScopeId}/actions/adjust`, {
    projectId: stageProject.id, expectedProjectVersion: stageProject.version,
    expectedProjectScopeVersion: stageProject.scopeVersion, expectedOrderLineSourceVersion: lineA.sourceVersion,
    proposedQuantity: 12, serialNumbers: [], reason: 'F-COM-001 Chromium adjust A'
  }, { 'If-Match': String(assignedA.allocationVersion), 'Idempotency-Key': crypto.randomUUID() }, fullToken)
  const releasedA = await api('POST', `/api/v1/pms/delivery-scopes/${adjustedA.deliveryScopeId}/actions/release`, {
    projectId: stageProject.id, expectedProjectVersion: stageProject.version,
    expectedProjectScopeVersion: stageProject.scopeVersion, expectedOrderLineSourceVersion: lineA.sourceVersion,
    reason: 'F-COM-001 Chromium release A'
  }, { 'If-Match': String(adjustedA.allocationVersion), 'Idempotency-Key': crypto.randomUUID() }, fullToken)

  const invalidSerial = await expectDenied('AST无效SN', 'POST', '/api/v1/pms/delivery-scopes/actions/assign', {
    projectId: stageProject.id, expectedProjectScopeVersion: stageProject.scopeVersion, orderLineId: lineC.id,
    expectedOrderLineSourceVersion: lineC.sourceVersion, allocatedQuantity: 1,
    serialNumbers: ['SN-FCOM001-INVALID-001'], reason: 'F-COM-001 Chromium invalid AST'
  }, { 'If-Match': String(stageProject.version), 'Idempotency-Key': crypto.randomUUID() }, fullToken, 1016001000)

  const stageKey = crypto.randomUUID()
  const stageEntry = await api('POST', `/api/v1/pms/projects/${stageProject.id}/actions/enter-acceptance-stage`,
    { expectedTreeVersion: stageProject.treeVersion },
    { 'If-Match': String(stageProject.version), 'Idempotency-Key': stageKey }, fullToken)
  const stageReplay = await api('POST', `/api/v1/pms/projects/${stageProject.id}/actions/enter-acceptance-stage`,
    { expectedTreeVersion: stageProject.treeVersion },
    { 'If-Match': String(stageProject.version), 'Idempotency-Key': stageKey }, fullToken)
  const stageScope = await api('POST', '/api/v1/pms/delivery-scopes/actions/assign', {
    projectId: stageProject.id, expectedProjectScopeVersion: stageProject.scopeVersion, orderLineId: lineB.id,
    expectedOrderLineSourceVersion: lineB.sourceVersion, allocatedQuantity: 4, serialNumbers: [],
    reason: 'F-COM-001 Chromium stage-active scope'
  }, { 'If-Match': String(stageEntry.projectVersion), 'Idempotency-Key': crypto.randomUUID() }, fullToken)

  const reductionPayload = structuredClone(importPayload)
  reductionPayload.sourceBatchId = `${importPayload.sourceBatchId}-V2`
  reductionPayload.contracts = []
  reductionPayload.salesOrders = []
  reductionPayload.salesOrderLines = [{ ...reductionPayload.salesOrderLines[1], sourceVersion: '2',
    orderQuantity: 2, openQuantity: 2, deliveredQuantity: 0,
    sourceUpdatedAt: Date.parse('2026-08-29T22:30:00+08:00') }]
  const reductionKey = crypto.randomUUID()
  const reduced = await api('POST', '/api/v1/pms/commerce-authority/import-batches', reductionPayload,
    { 'Idempotency-Key': reductionKey, 'X-Source-System': 'ERP' }, fullToken)
  const reductionReplay = await api('POST', '/api/v1/pms/commerce-authority/import-batches', reductionPayload,
    { 'Idempotency-Key': reductionKey, 'X-Source-System': 'ERP' }, fullToken)
  const frozenAssign = await expectDenied('冲突冻结后禁止新分配', 'POST',
    '/api/v1/pms/delivery-scopes/actions/assign', {
      projectId: stageProject.id, expectedProjectScopeVersion: stageProject.scopeVersion, orderLineId: lineB.id,
      expectedOrderLineSourceVersion: '2', allocatedQuantity: 1, serialNumbers: [], reason: 'frozen negative'
    }, { 'If-Match': String(stageEntry.projectVersion), 'Idempotency-Key': crypto.randomUUID() }, fullToken, 1016001001)

  await uiLogin(managedUser.username)
  consoleErrors.length = 0
  pageErrors.length = 0
  networkFailures.length = 0
  await navigate(`${appUrl}/customer-asset/commerce-contracts`)
  const contractUi = {
    routeLoaded: await bodyIncludes('合同与订单'),
    inputSet: await setInput('合同编号', `FCOM-${suffix}`),
    searchClicked: await clickText('查询')
  }
  await wait(1400)
  contractUi.contractVisible = await bodyIncludes(`FCOM-${suffix}`)
  contractUi.orderVisible = await bodyIncludes(`SO-FCOM-${suffix}`)

  await navigate(`${appUrl}/customer-asset/delivery-scopes?projectId=${stageProject.id}&projectVersion=1&projectScopeVersion=1`)
  await wait(1400)
  const rootScopeUi = {
    routeLoaded: await bodyIncludes('交付范围'),
    frozenVisible: await bodyIncludes('CONFLICT_FROZEN'),
    historyOpened: await clickText('历史')
  }
  await wait(700)
  rootScopeUi.historyVisible = await bodyIncludes('范围历史') || await bodyIncludes('分配版本')

  const responsive = {}
  for (const [name, width, height] of viewports) {
    await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width <= 768 })
    await wait(400)
    responsive[name] = await evaluate(`({
      viewport: [innerWidth, innerHeight],
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
      pageVisible: document.body.innerText.includes('交付范围'),
      frozenVisible: document.body.innerText.includes('CONFLICT_FROZEN')
    })`)
    await screenshot(`delivery-scope-${name}`)
  }
  await send('Emulation.clearDeviceMetricsOverride')
  await screenshot('stage-project-binding')

  const scopeHistoryRows = mysql(`SELECT order_line_id,id,scope_status,allocation_version,IF(effective_to IS NULL,'CURRENT','HISTORY') FROM com_delivery_scope WHERE tenant_id=0 AND order_line_id IN (${lineA.id},${lineB.id}) ORDER BY order_line_id,allocation_version`)
  const outboxRows = mysql(`SELECT event_type,aggregate_key,scope_version,status FROM com_outbox_event WHERE tenant_id=0 AND event_type='NotificationRequested' AND aggregate_key='${stageScope.deliveryScopeId}' ORDER BY id`)
  const stageSnapshotRows = mysql(`SELECT id,project_id,stage_code,operation_type FROM proj_project_stage_snapshot WHERE tenant_id=0 AND project_id=${stageProject.id} AND operation_type='STAGE_ENTRY' ORDER BY id`)
  const bindingRows = mysql(`SELECT project_id,delivery_scope_id,scope_allocation_version,binding_trigger,binding_status FROM acc_acceptance_scope_binding WHERE tenant_id=0 AND project_id=${stageProject.id} ORDER BY delivery_scope_id,scope_allocation_version`)
  const invalidSerialRows = mysql(`SELECT COUNT(*) FROM com_delivery_scope WHERE tenant_id=0 AND order_line_id=${lineC.id}`)

  const negatives = [importConflict, noAuthority, invalidSerial, frozenAssign]
  const unexpectedNetworkFailures = networkFailures.filter((item) => item.errorText !== 'net::ERR_ABORTED')
  const checks = {
    sensitivePermissionPositive: seedSensitive.contractType != null && seedSensitive.currencyCode != null,
    sensitivePermissionNegative: seedMasked.contractType == null && seedMasked.customerCode == null
      && seedMasked.customerName == null && seedMasked.currencyCode == null,
    importAndReplay: imported.replayed === false && importReplay.replayed === true,
    contractOrderLineVisible: Boolean(contract && order && lineA && lineB && lineC),
    lockedFiltersEffective: seededFiltered.list.some((item) => item.id === 992002390001),
    contractDetailComplete: contractDetail.contract.id === contract.id
      && seedDetail.relatedOrders.some((item) => item.id === 992002399001)
      && seedDetail.projectRelations.some((item) => item.projectId === rootProject.id)
      && contractDetail.sourceSystem === 'ERP' && contractDetail.sourceVersion === '1'
      && Boolean(contractDetail.sourceUpdatedAt),
    previewAllowed: previewA.allowed === true,
    adjustmentVersioned: adjustedA.allocationVersion > assignedA.allocationVersion,
    releaseClosedCurrent: releasedA.deliveryScopeId === adjustedA.deliveryScopeId
      && releasedA.allocationVersion === adjustedA.allocationVersion,
    allNegativesDenied: negatives.every((item) => item.denied),
    reductionReplay: reduced.replayed === false && reductionReplay.replayed === true,
    stageEntryReplay: stageEntry.replayed === false && stageReplay.replayed === true
      && stageEntry.projectStageSnapshotId === stageReplay.projectStageSnapshotId,
    stageActiveScopeCreated: Boolean(stageScope.deliveryScopeId),
    historyPreserved: scopeHistoryRows.includes('HISTORY') && scopeHistoryRows.includes('RELEASED')
      && scopeHistoryRows.includes('CONFLICT_FROZEN'),
    singleNotification: outboxRows.split(/\r?\n/).filter(Boolean).length === 1,
    singleStageSnapshot: stageSnapshotRows.split(/\r?\n/).filter(Boolean).length === 1,
    stageBindingsPresent: bindingRows.includes('PROJECT_STAGE_ENTRY') && bindingRows.includes('SCOPE_VERSION_EFFECTIVE'),
    invalidSerialZeroWrite: invalidSerialRows === '0',
    contractUi: Object.values(contractUi).every(Boolean),
    rootScopeUi: Object.values(rootScopeUi).every(Boolean),
    stageScopeUi: rootScopeUi.routeLoaded && rootScopeUi.frozenVisible,
    responsive: Object.values(responsive).every((item) => item.pageVisible && item.frozenVisible && !item.overflow),
    browserErrorsAbsent: consoleErrors.length === 0 && pageErrors.length === 0
      && unexpectedNetworkFailures.length === 0
  }
  const result = {
    generatedAt: new Date().toISOString(),
    feature: 'F-COM-001', requirementIds: ['COM-01@V1', 'PM-03', 'PM-10', 'ACC-03'],
    ports: { chrome: 9224, backend: 59280, frontend: 19081, mysql: 23316, redis: 26379 },
    users: { managed: managedUser.username },
    created: { contractId: contract.id, orderId: order.id, orderLineIds: [lineA.id, lineB.id, lineC.id],
      adjustedScopeId: adjustedA.deliveryScopeId, stageScopeId: stageScope.deliveryScopeId },
    negatives, contractUi, rootScopeUi, responsive,
    databaseEvidence: { scopeHistoryRows, outboxRows, stageSnapshotRows, bindingRows, invalidSerialRows },
    consoleErrors, pageErrors, networkFailures, unexpectedNetworkFailures, checks,
    pass: Object.values(checks).every(Boolean)
  }
  fs.writeFileSync(path.join(outputDir, '..', 'f-com-001-browser-evidence.json'), `${JSON.stringify(result, null, 2)}\n`)
  console.log(JSON.stringify(result, null, 2))
  await send('Browser.close')
  if (!result.pass) process.exitCode = 1
})().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
