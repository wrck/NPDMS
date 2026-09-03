const fs = require('node:fs')
const path = require('node:path')
const os = require('node:os')
const { execFileSync } = require('node:child_process')

const endpoint = process.env.NPDMS_BROWSER_CDP_URL || 'http://127.0.0.1:9224'
const appUrl = process.env.NPDMS_BROWSER_APP_URL || 'http://127.0.0.1:19340'
const apiUrl = process.env.NPDMS_BROWSER_API_URL || 'http://127.0.0.1:59340/admin-api'
const password = process.env.FACC002_BROWSER_PASSWORD
if (!password) throw new Error('必须通过FACC002_BROWSER_PASSWORD提供正式验收密码')

const username = 'fcom001acceptance'
const managedRoleId = 992002800001
const managedUserId = 992002800002
const acceptanceRoleId = 992004800002
const evidenceFile = path.resolve('docs/engineering/evidence/f-acc-002-browser-evidence.json')
const screenshotDir = path.resolve('docs/engineering/evidence/f-acc-002-browser')
fs.mkdirSync(screenshotDir, { recursive: true })

const assert = (condition, message) => { if (!condition) throw new Error(message) }
const mysql = (sql) => execFileSync('docker', [
  'exec', 'npdms-50eb-runtime-mysql-1', 'sh', '-c',
  'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" npdms -e "$1" 2>/dev/null', '_', sql
], { encoding: 'utf8' }).trim()

;(async () => {
  const target = await fetch(`${endpoint}/json/new?about:blank`, { method: 'PUT' }).then((r) => r.json())
  assert(target.webSocketDebuggerUrl, '无法创建独立Chromium验收页签')
  const socket = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true })
    socket.addEventListener('error', reject, { once: true })
  })

  let sequence = 0
  const pending = new Map()
  const consoleErrors = []
  const pageErrors = []
  const requestFailures = []
  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    if (message.id && pending.has(message.id)) {
      const callback = pending.get(message.id)
      pending.delete(message.id)
      return message.error ? callback.reject(new Error(message.error.message)) : callback.resolve(message.result)
    }
    if (message.method === 'Runtime.exceptionThrown') {
      pageErrors.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text)
    }
    if (message.method === 'Log.entryAdded' && message.params.entry.level === 'error') consoleErrors.push(message.params.entry.text)
    if (message.method === 'Network.loadingFailed' && !message.params.canceled) requestFailures.push(message.params.errorText)
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
  const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
  const waitUntil = async (probe, message, timeoutMs = 120000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      const value = await probe()
      if (value) return value
      await wait(1000)
    }
    throw new Error(message)
  }
  const navigate = async (url) => { await send('Page.navigate', { url }); await wait(2500) }
  const screenshot = async (name) => {
    const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    fs.writeFileSync(path.join(screenshotDir, name), Buffer.from(image.data, 'base64'))
  }
  const rawApi = (method, url, data, headers = {}, token, tenantId = 0) => evaluate(`(async () => {
    const response = await fetch(${JSON.stringify(apiUrl)} + ${JSON.stringify(url)}, {
      method: ${JSON.stringify(method)},
      headers: { 'tenant-id': ${JSON.stringify(String(tenantId))}, ...${JSON.stringify(headers)},
        ...(${token ? `{ authorization: 'Bearer ' + ${JSON.stringify(token)} }` : '{}'}),
        ...(${data === undefined ? '{}' : "{ 'Content-Type': 'application/json' }"}) },
      ...(${data === undefined ? '{}' : `{ body: JSON.stringify(${JSON.stringify(data)}) }`})
    });
    const text = await response.text(); let body;
    try { body = JSON.parse(text) } catch { body = { raw: text } }
    return { status: response.status, body };
  })()`)
  const api = async (...args) => {
    const response = await rawApi(...args)
    assert(response.status < 400 && response.body?.code === 0,
      `公开API失败：HTTP ${response.status}, code=${response.body?.code}, msg=${response.body?.msg}`)
    return response.body.data
  }
  const assertDeniedWithoutLeak = (response, protectedIds, message) => {
    assert(response.status >= 400 || response.body?.code !== 0, `${message}：请求未被拒绝`)
    assert(response.body?.data == null, `${message}：拒绝响应错误返回业务数据`)
    const responseText = JSON.stringify(response.body)
    assert(protectedIds.every((id) => !responseText.includes(String(id))), `${message}：拒绝响应泄露对象身份`)
  }
  const key = (prefix) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
  const login = async (loginUsername, tenantId = 0) => {
    const response = await rawApi('POST', '/system/auth/login',
      { username: loginUsername, password, captchaVerification: '' }, {}, undefined, tenantId)
    assert(response.status === 200 && response.body?.code === 0 && response.body?.data?.accessToken,
      `${loginUsername}正式登录失败：HTTP ${response.status}, code=${response.body?.code}, msg=${response.body?.msg}`)
    return response.body.data
  }
  const clickText = (text) => evaluate(`(() => {
    const target = [...document.querySelectorAll('button,a,[role="button"],[role="tab"]')]
      .find((node) => node.offsetParent !== null
        && (node.innerText || node.textContent || '').trim() === ${JSON.stringify(text)} && !node.disabled);
    if (!target) return false; target.click(); return true;
  })()`)
  const setTestInput = (testId, value) => evaluate(`(() => {
    const root = document.querySelector('[data-testid="' + ${JSON.stringify(testId)} + '"]');
    const input = root?.matches('input,textarea') ? root : root?.querySelector('input,textarea');
    if (!input) return false;
    const prototype = input instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(prototype, 'value').set.call(input, ${JSON.stringify(value)});
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  })()`)
  const setFileInput = async (selector, filePath) => {
    const { root } = await send('DOM.getDocument', { depth: -1, pierce: true })
    const { nodeId } = await send('DOM.querySelector', { nodeId: root.nodeId, selector })
    assert(nodeId, `找不到文件输入：${selector}`)
    await send('DOM.setFileInputFiles', { nodeId, files: [filePath] })
  }
  const clickTaskAction = (taskId, label) => evaluate(`(() => {
    const row = [...document.querySelectorAll('.el-table__row')]
      .find((item) => (item.innerText || '').includes(${JSON.stringify(String(taskId))}));
    const button = row && [...row.querySelectorAll('button')]
      .find((item) => (item.innerText || '').trim() === ${JSON.stringify(label)} && !item.disabled);
    if (!button) return false; button.click(); return true;
  })()`)
  const uiLogin = async () => {
    await send('Storage.clearDataForOrigin', { origin: new URL(appUrl).origin, storageTypes: 'all' })
    await navigate(`${appUrl}/login`)
    const filled = await evaluate(`(() => {
      const inputs = [...document.querySelectorAll('input')];
      const user = inputs.find((input) => input.type === 'text');
      const pass = inputs.find((input) => input.type === 'password');
      if (!user || !pass) return false;
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
      setter.call(user, ${JSON.stringify(username)}); user.dispatchEvent(new Event('input', { bubbles: true }));
      setter.call(pass, ${JSON.stringify(password)}); pass.dispatchEvent(new Event('input', { bubbles: true }));
      return true;
    })()`)
    assert(filled && await clickText('登录'), '公开登录表单不可操作')
    await waitUntil(() => evaluate(`!location.pathname.includes('/login')`), '正式验收身份未离开登录页', 30000)
  }
  const uploadAcceptanceFile = (token, reportVersionId, name) => evaluate(`(async () => {
    const headers = { 'tenant-id': '0', authorization: 'Bearer ' + ${JSON.stringify(token)},
      'Content-Type': 'application/json' };
    const referenceKey = crypto.randomUUID();
    const content = new TextEncoder().encode('%PDF-1.4\\nF-ACC-002 runtime acceptance\\n%%EOF');
    const init = await fetch(${JSON.stringify(apiUrl)} + '/api/v1/pms/files:init-upload', { method: 'POST',
      headers: { ...headers, 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ ownerContext: 'ACC',
      objectType: 'ACCEPTANCE_REPORT_VERSION', objectId: ${JSON.stringify(String(reportVersionId))},
      purposeCode: 'ACCEPTANCE_REPORT_ATTACHMENT', referenceKey, modeCode: 'CREATE_ARTIFACT',
      fileName: ${JSON.stringify(name)}, categoryCode: 'ACCEPTANCE_REPORT_ATTACHMENT',
      declaredSizeBytes: content.byteLength, declaredMediaType: 'application/pdf' }) }).then(r => r.json());
    if (init.code !== 0) return init;
    const form = new FormData(); form.append('sessionId', String(init.data.sessionId));
    form.append('file', new File([content], ${JSON.stringify(name)}, { type: 'application/pdf' }));
    return fetch(${JSON.stringify(apiUrl)} + '/api/v1/pms/files/' + init.data.artifactId + ':complete-upload', {
      method: 'POST', headers: { 'tenant-id': '0', authorization: 'Bearer ' + ${JSON.stringify(token)},
      'Idempotency-Key': crypto.randomUUID() }, body: form }).then(r => r.json());
  })()`)
  const uploadGrantFile = (grantToken, requestId, policyKey, ordinal, fileName) => evaluate(`(async () => {
    const base = ${JSON.stringify(apiUrl)}; const token = ${JSON.stringify(grantToken)};
    const requestId = ${JSON.stringify(requestId)}; const policyKey = ${JSON.stringify(policyKey)};
    const operationId = 'g:' + (policyKey === 'SATISFACTION_SIGNATURE' ? 's' : 'a')
      + ':' + ${ordinal} + ':' + Date.now();
    const content = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='), c => c.charCodeAt(0));
    const headers = { 'tenant-id': '0', 'Content-Type': 'application/json' };
    const initialized = await fetch(base + '/api/v1/pms/satisfaction-questionnaires/' + encodeURIComponent(token)
      + '/files/initialize', { method: 'POST', headers, body: JSON.stringify({ requestId, policyKey, operationId,
      fileName: ${JSON.stringify(fileName)}, categoryCode: policyKey, declaredSizeBytes: content.byteLength,
      declaredMediaType: 'image/png' }) }).then(r => r.json());
    if (initialized.code !== 0) return { initialized };
    const metadata = { requestId, responseId: initialized.data.responseId, policyKey, operationId,
      fileSlotKey: initialized.data.fileSlotKey, fileSequence: initialized.data.fileSequence,
      artifactId: initialized.data.artifactId };
    const form = new FormData();
    form.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
    form.append('file', new File([content], ${JSON.stringify(fileName)}, { type: 'image/png' }));
    const completed = await fetch(base + '/api/v1/pms/satisfaction-questionnaires/' + encodeURIComponent(token)
      + '/files/' + initialized.data.sessionId + '/complete', { method: 'POST',
      headers: { 'tenant-id': '0' }, body: form }).then(r => r.json());
    return { initialized, completed };
  })()`)
  await Promise.all([send('Page.enable'), send('Runtime.enable'), send('Network.enable'), send('Log.enable')])
  await send('Page.addScriptToEvaluateOnNewDocument', { source: `
    window.__facc002BusinessErrors = [];
    addEventListener('DOMContentLoaded', () => new MutationObserver((mutations) => {
      for (const mutation of mutations) for (const node of mutation.addedNodes) {
        if (node.nodeType !== Node.ELEMENT_NODE) continue;
        const candidates = node.matches?.('.el-message--error') ? [node]
          : [...(node.querySelectorAll?.('.el-message--error') || [])];
        for (const item of candidates) window.__facc002BusinessErrors.push(item.innerText || item.textContent || '业务错误');
      }
    }).observe(document.body, { childList: true, subtree: true }));
  ` })
  await navigate(appUrl)

  const admin = await login('admin')
  const runtimeFileConfig = await api('GET', '/infra/file-config/get?id=28', undefined, {}, admin.accessToken)
  if (runtimeFileConfig.config.enablePublicAccess !== false
      || runtimeFileConfig.config.enablePathStyleAccess !== true) {
    await api('PUT', '/infra/file-config/update', {
      id: runtimeFileConfig.id,
      name: runtimeFileConfig.name,
      storage: runtimeFileConfig.storage,
      config: {
        ...runtimeFileConfig.config,
        enablePathStyleAccess: true,
        enablePublicAccess: false
      },
      remark: runtimeFileConfig.remark
    }, {}, admin.accessToken)
  }
  await api('PUT', '/infra/file-config/update-master?id=28', undefined, {}, admin.accessToken)
  const existingMenus = await api('GET', `/system/permission/list-role-menus?roleId=${managedRoleId}`,
    undefined, {}, admin.accessToken)
  const requiredMenuIds = [18067, 18068, 18070, 930921, 930922, 930923, 930924, 930925, 930926,
    930931, 930932, 930933, 930934, 930935, 198780, 198781, 198782, 198783, 198785]
  const configuredMenus = [...new Set([...existingMenus, ...requiredMenuIds])]
  await api('POST', '/system/permission/assign-role-menu',
    { roleId: managedRoleId, menuIds: configuredMenus }, {}, admin.accessToken)
  const acceptanceMenus = await api('GET',
    `/system/permission/list-role-menus?roleId=${acceptanceRoleId}`, undefined, {}, admin.accessToken)
  await api('POST', '/system/permission/assign-role-menu', {
    roleId: acceptanceRoleId, menuIds: [...new Set([...acceptanceMenus, 18067, 18068])]
  }, {}, admin.accessToken)
  const userRoles = await api('GET',
    `/system/permission/list-user-roles?userId=${managedUserId}`, undefined, {}, admin.accessToken)
  await api('POST', '/system/permission/assign-user-role', {
    userId: managedUserId, roleIds: [...new Set([...userRoles, acceptanceRoleId])]
  }, {}, admin.accessToken)
  await api('PUT', '/system/user/update-password',
    { id: managedUserId, password }, {}, admin.accessToken)

  const staleAuth = await login(username)
  await api('POST', '/system/auth/logout', undefined, {}, staleAuth.accessToken)
  const auth = await login(username)
  const token = auth.accessToken
  const permissions = await api('GET', '/system/auth/get-permission-info', undefined, {}, token)
  for (const permission of ['pms:project:create', 'pms:project:query', 'pms:project:assign',
    'pms:project-task:assign', 'pms:project-task:execute',
    'pms:acceptance:report:query', 'pms:acceptance:report:write', 'pms:acceptance:report:complete',
    'pms:acceptance:satisfaction:query', 'pms:acceptance:satisfaction:manage',
    'pms:acceptance:satisfaction:collect', 'pms:acceptance:satisfaction:export',
    'pms:acceptance:satisfaction:download', 'pms:file:upload', 'pms:file:download', 'pms:file:archive']) {
    assert(permissions.permissions.includes(permission),
      `正式身份缺少权限：${permission}；当前权限=${permissions.permissions.join(',')}`)
  }
  await uiLogin()

  const marker = Date.now()
  const questionnaireJson = JSON.stringify({
    schemaVersion: 1,
    questions: [{ code: 'Q1', title: '您对本项目交付是否满意？', type: 'SINGLE_CHOICE', required: true,
      options: [{ code: 'LOW', label: '需要整改', score: '20.00' },
        { code: 'HIGH', label: '满意', score: '100.00' }] }],
    scoring: { ruleVersion: 'SUM_V1', strategy: 'SUM_V1', scoreMin: '0.00', scoreMax: '100.00',
      precision: 2, roundingMode: 'HALF_UP', threshold: '80.00' }
  })
  const templates = await api('GET', '/api/v1/pms/satisfaction-questionnaire-templates', undefined, {}, token)
  let template = templates.find((item) => item.revisions?.some((itemRevision) =>
    itemRevision.status === 'PUBLISHED' && itemRevision.projectType === 'STANDARD'
      && itemRevision.signingMode === 'DIRECT_SIGN' && itemRevision.implementationMode === 'DIRECT_SERVICE'
      && itemRevision.businessPurposeCode === 'ACCEPTANCE'
      && itemRevision.applicableTimingCode === 'AFTER_INITIAL_ACCEPTANCE'))
  let revision = template?.revisions.find((itemRevision) => itemRevision.status === 'PUBLISHED'
    && itemRevision.projectType === 'STANDARD' && itemRevision.signingMode === 'DIRECT_SIGN'
    && itemRevision.implementationMode === 'DIRECT_SERVICE'
    && itemRevision.businessPurposeCode === 'ACCEPTANCE'
    && itemRevision.applicableTimingCode === 'AFTER_INITIAL_ACCEPTANCE')
  if (!revision) {
    template = await api('POST', '/api/v1/pms/satisfaction-questionnaire-templates',
      { templateCode: `FACC002-RUNTIME-${marker}`, name: `F-ACC-002 正向验收模板 ${marker}` }, {}, token)
    revision = await api('POST', `/api/v1/pms/satisfaction-questionnaire-templates/${template.id}/revisions`, {
      projectType: 'STANDARD', signingMode: 'DIRECT_SIGN', implementationMode: 'DIRECT_SERVICE',
      businessPurposeCode: 'ACCEPTANCE', applicableTimingCode: 'AFTER_INITIAL_ACCEPTANCE', priority: 1,
      questionnaireJson, threshold: 80, ruleVersion: 'SUM_V1'
    }, {}, token)
    await api('POST', `/api/v1/pms/satisfaction-questionnaire-templates/${template.id}/revisions/${revision.id}/actions/publish`,
      { expectedRevisionVersion: revision.version }, { 'Idempotency-Key': key('publish-template') }, token)
  }

  const matched = await api('GET',
    '/pms/projects/actions/match-templates?signingMethod=DIRECT_SIGN&projectCategory=ENGINEERING&implementationMode=DIRECT_SERVICE',
    undefined, {}, token)
  const projectTemplate = matched.candidates.find((candidate) => candidate.templateRevisionId === 911003)
    || matched.candidates.find((candidate) => candidate.templateRevisionId)
  assert(projectTemplate, '项目模板匹配未返回可选发布修订')
  const project = await api('POST', '/pms/projects', {
    projectName: `F-ACC-002 正向验收项目 ${marker}`,
    customerCode: `FACC002-${marker}`, customerName: 'F-ACC-002 受控客户',
    orderOfficeCompanyId: 930850, orderOfficeDepartmentId: 930851,
    serviceManagerUserId: managedUserId,
    implementationLocation: '杭州受控验收地点', signingMethod: 'DIRECT_SIGN',
    projectCategory: 'ENGINEERING', implementationMode: 'DIRECT_SERVICE',
    creationReason: 'F-ACC-002 真实Chromium正向闭环',
    templateRevisionId: projectTemplate.templateRevisionId,
    candidateWatermark: matched.candidateWatermark
  }, { 'Idempotency-Key': key('create-project') }, token)
  const projectId = project.id
  assert(projectId, '公开项目创建没有返回项目ID')

  let acceptances = await api('GET', `/api/v1/pms/acceptances?projectId=${projectId}`, undefined, {}, token)
  const preliminary = acceptances.find((item) => item.acceptanceType === 'PRELIMINARY')
  assert(preliminary, '新项目未形成初验活动')
  const draft = await api('POST', `/api/v1/pms/acceptances/${preliminary.id}/report-versions`, {
    acceptanceTime: '2026-08-30T15:00:00', conclusionCode: 'PASS',
    conclusionText: 'F-ACC-002 初验完成触发满意度', acceptorName: '正式验收人'
  }, { 'If-Match': String(preliminary.version) }, token)
  const uploaded = await uploadAcceptanceFile(token, draft.reportVersionId, `facc002-preliminary-${marker}.pdf`)
  assert(uploaded.code === 0, `初验附件上传失败：${uploaded.msg}`)
  await api('POST', `/api/v1/pms/acceptances/${preliminary.id}/report-versions/${draft.reportVersionId}/actions/publish`, {
    expectedReportVersionNo: draft.reportVersionNo, expectedCurrentReportVersionId: null
  }, { 'If-Match': String(preliminary.version), 'Idempotency-Key': key('publish-report') }, token)
  const reportVersions = await api('GET', `/api/v1/pms/acceptances/${preliminary.id}/report-versions`,
    undefined, {}, token)
  const effectiveReport = reportVersions.find((item) => item.reportStatus === 'EFFECTIVE')
  assert(effectiveReport?.attachments?.length === 1, '初验当前报告或附件事实不完整')
  const reportAttachment = effectiveReport.attachments[0]
  const satisfactionTaskTree = await api('GET',
    `/api/v1/pms/projects/${projectId}/tasks?mode=LOCATE&keyword=T-SAT-SURVEY&pageSize=200`,
    undefined, {}, token)
  const satisfactionProjectTask = satisfactionTaskTree.rows.find((item) => item.taskCode === 'T-SAT-SURVEY')
  assert(satisfactionProjectTask, '新项目未形成唯一T-SAT-SURVEY任务')
  await api('POST', `/api/v1/pms/project-tasks/${satisfactionProjectTask.taskId}/actions/assign`, {
    assigneeUserId: managedUserId, reason: 'F-ACC-002 满意度任务正式指派'
  }, { 'If-Match': String(satisfactionProjectTask.version),
    'Idempotency-Key': key('assign-satisfaction') }, token)
  let workbench = await api('GET', `/api/v1/pms/project-tasks/${preliminary.projectTaskId}/workbench`,
    undefined, {}, token)
  const assigned = await api('POST',
    `/api/v1/pms/project-tasks/${preliminary.projectTaskId}/actions/assign`, {
      assigneeUserId: managedUserId, reason: 'F-ACC-002 初验任务正式指派'
    }, { 'If-Match': String(workbench.task.version), 'Idempotency-Key': key('assign-initial') }, token)
  const started = await api('POST',
    `/api/v1/pms/project-tasks/${preliminary.projectTaskId}/actions/start`, {
      reason: 'F-ACC-002 初验任务开始'
    }, { 'If-Match': String(assigned.taskVersion), 'Idempotency-Key': key('start-initial') }, token)
  const submitted = await api('POST',
    `/api/v1/pms/project-tasks/${preliminary.projectTaskId}/actions/submit`, {
      reason: 'F-ACC-002 初验任务提交完成'
    }, { 'If-Match': String(started.taskVersion), 'Idempotency-Key': key('submit-initial') }, token)
  workbench = await api('GET', `/api/v1/pms/project-tasks/${preliminary.projectTaskId}/workbench`,
    undefined, {}, token)
  const currentAcceptance = await api('GET', `/api/v1/pms/acceptances/${preliminary.id}`, undefined, {}, token)
  const completed = await api('POST', `/api/v1/pms/project-tasks/${preliminary.projectTaskId}/actions/complete`, {
    reason: 'F-ACC-002 初验完成触发满意度', executionContractId: workbench.executionContractId,
    contractVersion: workbench.contractVersion, factObjectKey: String(preliminary.id),
    expectedActivityVersion: currentAcceptance.version, expectedReportVersion: effectiveReport.reportVersionNo
  }, { 'If-Match': String(submitted.taskVersion), 'Idempotency-Key': key('complete-initial') }, token)
  assert(completed.status === 'DONE', '初验任务未通过公开命令完成')

  const revisionOne = await waitUntil(async () => {
    const tasks = await api('GET', `/api/v1/pms/satisfaction-tasks?projectId=${projectId}`, undefined, {}, token)
    return tasks.find((item) => item.revisionNo === 1)
  }, '初验完成后未形成满意度revision1')
  assert(revisionOne.assignedToUserId === managedUserId, 'revision1未指派给项目正式责任人')
  await navigate(`${appUrl}/pms/project/satisfaction?projectId=${projectId}`)
  await waitUntil(() => evaluate(`document.body.innerText.includes(${JSON.stringify(String(revisionOne.id))})`),
    '满意度工作台未渲染revision1')
  await screenshot('01-satisfaction-workbench.png')
  assert(await clickTaskAction(revisionOne.id, '现场协助'), '工作台无法打开revision1现场协助对话框')
  await waitUntil(() => evaluate(`Boolean(document.querySelector('[data-testid="assisted-submit"]'))`),
    '现场协助对话框未渲染')
  assert(await setTestInput('assisted-customer-contact', '受控客户现场确认'), '现场协助联系人不可输入')
  assert(await setTestInput('assisted-answer',
    JSON.stringify({ answers: [{ questionCode: 'Q1', value: 'LOW' }] })), '现场协助答卷不可输入')
  const assistedSignaturePath = path.join(os.tmpdir(), `facc002-assisted-signature-${marker}.png`)
  fs.writeFileSync(assistedSignaturePath, Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64'))
  await setFileInput('[data-testid="assisted-signature-upload"] input[type="file"]', assistedSignaturePath)
  await waitUntil(() => evaluate(`document.body.innerText.includes(${JSON.stringify(path.basename(assistedSignaturePath))})`),
    '现场协助签字文件未进入对话框')
  await screenshot('01-assisted-response-dialog.png')
  assert(await clickText('上传并提交'), '现场协助上传并提交按钮不可操作')
  const low = await waitUntil(async () => {
    const results = await api('GET', `/api/v1/pms/satisfaction-results?projectId=${projectId}`, undefined, {}, token)
    return results.find((item) => item.taskId === revisionOne.id)
  }, '工作台现场协助未形成revision1 Result')
  assert(low.score === 20 && low.passed === false, 'revision1现场协助未形成低分未达标Result')

  const revisionTwoCreated = await api('POST', `/api/v1/pms/satisfaction-tasks/${revisionOne.id}/actions/recollect`, {
    priorResultId: low.resultId, remediationRequestId: key('remediation'),
    evidenceSummary: '已完成客户反馈整改并发起第二轮问卷'
  }, {}, token)
  const revisionTwo = await waitUntil(async () => {
    const tasks = await api('GET', `/api/v1/pms/satisfaction-tasks?projectId=${projectId}`, undefined, {}, token)
    return tasks.find((item) => item.id === revisionTwoCreated.taskId && item.revisionNo === 2)
  }, '整改后未形成revision2')

  await navigate(`${appUrl}/pms/project/satisfaction?projectId=${projectId}`)
  await wait(2500)
  await screenshot('01-satisfaction-workbench.png')
  const grantOpened = await evaluate(`(() => {
    const rows = [...document.querySelectorAll('.el-table__row')];
    const row = rows.find((item) => (item.innerText || '').includes(${JSON.stringify(String(revisionTwo.id))}));
    const button = row && [...row.querySelectorAll('button')].find((item) => (item.innerText || '').trim() === '受控链接');
    if (!button) return false; button.click(); return true;
  })()`)
  assert(grantOpened, '工作台无法打开revision2受控链接对话框')
  await wait(500)
  assert(await clickText('创建链接'), '工作台无法创建受控链接')
  const grantUrl = await waitUntil(() => evaluate(`(() => {
    const input = [...document.querySelectorAll('.el-dialog input')]
      .find((item) => String(item.value || '').includes('/satisfaction-questionnaires/'));
    return input?.value || '';
  })()`), '工作台未展示受控链接')
  assert(documentUrl(grantUrl).searchParams.get('tenantId') === '0', '受控链接缺少租户边界')
  const grantToken = decodeURIComponent(documentUrl(grantUrl).pathname.split('/').at(-1))
  assert(grantToken && !grantToken.includes('/'), '受控链接token格式错误')
  const qrRendered = await evaluate(`document.querySelectorAll('.el-dialog canvas,.el-dialog svg').length > 0`)
  assert(qrRendered, '受控链接未渲染二维码')
  await screenshot('02-controlled-link-qr.png')

  await navigate(grantUrl)
  await waitUntil(() => evaluate(`document.body.innerText.includes('项目满意度调查')`), '匿名问卷页未加载')
  const anonymousStayedPublic = await evaluate(`!location.pathname.includes('/login')`)
  assert(anonymousStayedPublic, '精确匿名问卷路由被重定向到登录页')
  await screenshot('03-public-questionnaire.png')

  const publicQuestionnaire = await api('GET', `/api/v1/pms/satisfaction-questionnaires/${encodeURIComponent(grantToken)}`,
    undefined, {}, undefined)
  assert(!publicQuestionnaire.frozenQuestions.includes('score') && !publicQuestionnaire.frozenQuestions.includes('threshold'),
    '匿名问卷错误泄露服务端计分配置')
  const publicRequestId = key('public-high')
  const signatureUpload = await uploadGrantFile(grantToken, publicRequestId,
    'SATISFACTION_SIGNATURE', 1, `facc002-signature-${marker}.png`)
  assert(signatureUpload.completed?.code === 0,
    `匿名签字上传失败：${signatureUpload.initialized?.msg || signatureUpload.completed?.msg}`)
  const attachmentUpload = await uploadGrantFile(grantToken, publicRequestId,
    'SATISFACTION_ATTACHMENT', 1, `facc002-attachment-${marker}.png`)
  assert(attachmentUpload.completed?.code === 0,
    `匿名附件上传失败：${attachmentUpload.initialized?.msg || attachmentUpload.completed?.msg}`)
  const grantFacts = [signatureUpload.completed.data, attachmentUpload.completed.data]
  const responseIds = new Set([signatureUpload.initialized.data.responseId, attachmentUpload.initialized.data.responseId])
  assert(responseIds.size === 1, '同一答卷文件预留没有重放同一responseId')
  const publicFiles = grantFacts.map((fact) => ({
    role: fact.policyKey === 'SATISFACTION_SIGNATURE' ? 'SIGNATURE' : 'ATTACHMENT',
    fileSlotKey: fact.fileSlotKey, sequence: fact.fileSequence,
    artifactId: fact.fileFact.artifactId, versionNo: fact.fileFact.versionNo,
    referenceKey: fact.fileFact.referenceKey,
    artifactVersion: fact.fileFact.fileFactVersion.artifactVersion,
    referenceVersion: fact.fileFact.fileFactVersion.referenceVersion,
    availabilityVersion: fact.fileFact.fileFactVersion.availabilityVersion,
    scopeVersion: fact.fileFact.scopeVersion, sha256: fact.fileFact.sha256
  }))
  const high = await api('POST', `/api/v1/pms/satisfaction-questionnaires/${encodeURIComponent(grantToken)}/responses`, {
    requestId: publicRequestId, responseId: [...responseIds][0], customerContactRef: '匿名客户确认',
    answerSnapshot: JSON.stringify({ answers: [{ questionCode: 'Q1', value: 'HIGH' }] }), files: publicFiles
  }, {}, undefined)
  assert(high.score === 100 && high.passed === true, 'revision2匿名答卷未形成达标Result')

  await waitUntil(() => Number(mysql(`SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=0 AND event_type='SatisfactionResultVersionChanged' AND aggregate_key IN ('${low.resultId}','${high.resultId}') AND status='DELIVERED'`)) === 2,
    '正式Quartz未投递两轮满意度Result事件')
  await waitUntil(() => Number(mysql(`SELECT COUNT(*) FROM acc_project_deliverable_source_version WHERE tenant_id=0 AND source_object_type='SatisfactionResult' AND source_object_id='${high.resultId}' AND archive_status='ARCHIVED'`)) === 1,
    '达标Result未形成已归档满意度来源')

  await navigate(`${appUrl}/pms/project/satisfaction?projectId=${projectId}`)
  assert(await clickText('判定结果'), '无法打开满意度判定结果页')
  const resultProjectSet = await evaluate(`(() => {
    const input = [...document.querySelectorAll('.el-tab-pane input')]
      .find((item) => item.offsetParent !== null && item.type !== 'checkbox');
    if (!input) return false;
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(input, ${JSON.stringify(String(projectId))});
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  })()`)
  assert(resultProjectSet && await clickText('查询'), '判定结果项目查询不可操作')
  await waitUntil(() => evaluate(`document.body.innerText.includes(${JSON.stringify(String(high.resultId))})`),
    '判定结果页未渲染达标Result')
  await screenshot('04-results-and-export.png')

  const historicalDownloadFact = await api('GET',
    `/api/v1/pms/satisfaction-results/${high.resultId}/files/1/download`, undefined, {}, token)
  assert(historicalDownloadFact.role === 'RESULT_DOCUMENT', '达标Result历史文件首项不是结果文档')
  const historicalFile = historicalDownloadFact.file
  const historicalTicket = await api('POST',
    `/api/v1/pms/files/${historicalFile.artifactId}/access-tickets`, {
      versionNo: historicalFile.versionNo, operationCode: 'DOWNLOAD', ownerContext: 'ACC',
      objectType: 'SATISFACTION_RESULT', objectId: String(high.resultId),
      purposeCode: 'SATISFACTION_RESULT_DOCUMENT', referenceKey: historicalFile.referenceKey
    }, {}, token)
  const historicalContent = await fetch(historicalTicket.shortLivedUrl)
  assert(historicalContent.ok, `达标Result历史文件短链下载失败：HTTP ${historicalContent.status}`)
  const historicalContentBytes = (await historicalContent.arrayBuffer()).byteLength
  assert(historicalContentBytes > 0, '达标Result历史文件下载内容为空')
  const historicalDownloadAudits = Number(mysql(`SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=0 AND operation_code='FILE_ACCESS_TICKET_CREATE' AND aggregate_type='FileArtifact' AND aggregate_key='${historicalFile.artifactId}' AND result_code='SUCCESS'`))
  assert(historicalDownloadAudits > 0, '达标Result历史文件下载缺少成功审计')

  const exportTask = await api('POST', '/api/v1/pms/satisfaction-results/exports', {
    projectId, fields: ['resultId', 'projectId', 'taskRevisionNo', 'score', 'threshold', 'passed',
      'ruleVersion', 'resultStatus', 'archiveStatus', 'effectiveFrom'], includeFiles: false
  }, { 'Idempotency-Key': key('export') }, token)
  const succeededExport = await waitUntil(async () => {
    const fact = await api('GET', `/api/v1/pms/export-tasks/${exportTask.taskId}`, undefined, {}, token)
    return fact.status === 'SUCCEEDED' ? fact : null
  }, '统一异步导出未由正式Quartz执行成功')
  const exportTicket = await api('POST', `/api/v1/pms/export-tasks/${succeededExport.taskId}/access-ticket`,
    undefined, {}, token)
  assert(exportTicket.shortLivedUrl, '统一导出未返回受控下载票据')

  const resultFiles = Number(mysql(`SELECT COUNT(*) FROM acc_satisfaction_result_file WHERE tenant_id=0 AND result_id IN (${low.resultId},${high.resultId})`))
  const sourceSequences = mysql(`SELECT GROUP_CONCAT(attachment_sequence ORDER BY attachment_sequence) FROM acc_project_deliverable_source_attachment a JOIN acc_project_deliverable_source_version s ON s.id=a.deliverable_source_version_id AND s.tenant_id=a.tenant_id WHERE a.tenant_id=0 AND s.source_object_id='${high.resultId}'`)
  const exportAudits = Number(mysql(`SELECT COUNT(*) FROM plt_export_audit WHERE tenant_id=0 AND export_task_id=${succeededExport.taskId}`))
  assert(resultFiles >= 5, '两轮Result完整文件集合未落库')
  assert(sourceSequences === '1,2,3', `达标来源附件全局序号错误：${sourceSequences}`)
  assert(exportAudits > 0, '统一导出缺少永久审计')

  const businessErrors = await evaluate(`window.__facc002BusinessErrors || []`)
  assert(businessErrors.length === 0 && pageErrors.length === 0 && consoleErrors.length === 0
    && requestFailures.length === 0, 'Chromium页面存在业务、脚本、控制台或网络错误')

  const evidence = {
    feature: 'F-ACC-002', generatedAt: new Date().toISOString(), pass: true,
    identity: { username, userId: managedUserId, permissionCount: permissions.permissions.length },
    project: { projectId, projectCode: project.projectCode, projectTemplateRevisionId: projectTemplate.templateRevisionId },
    template: { templateId: template.id, templateRevisionId: revision.id, configurableServerScoring: true },
    revisionOne: { taskId: revisionOne.id, responseId: low.responseId, resultId: low.resultId,
      score: low.score, threshold: low.threshold, passed: low.passed, channel: 'ASSISTED' },
    revisionTwo: { taskId: revisionTwo.id, responseId: high.responseId, resultId: high.resultId,
      score: high.score, threshold: high.threshold, passed: high.passed, channel: 'PUBLIC_LINK',
      qrRendered, anonymousStayedPublic },
    projection: { resultFiles, sourceSequences, archivedCurrentSource: true, deliveredResultEvents: 2 },
    historicalDownload: { resultId: high.resultId, role: historicalDownloadFact.role,
      artifactId: historicalFile.artifactId, contentBytes: historicalContentBytes,
      auditCount: historicalDownloadAudits },
    export: { taskId: succeededExport.taskId, status: succeededExport.status, auditCount: exportAudits,
      accessTicketIssued: true },
    runtime: { backendPort: 59340, frontendPort: 19340, chromiumCdpPort: 9224,
      businessErrors: businessErrors.length, pageErrors: pageErrors.length,
      consoleErrors: consoleErrors.length, requestFailures: requestFailures.length },
    assertions: ['FORMAL_TEMPLATE_PUBLISHED', 'PUBLIC_PROJECT_CREATED_WITH_FROZEN_SATISFACTION_FACT',
      'INITIAL_ACCEPTANCE_TRIGGERED_REVISION_ONE', 'ASSISTED_LOW_SCORE_RESULT',
      'RECOLLECT_CREATED_REVISION_TWO', 'EXACT_ANONYMOUS_ROUTE_AND_QR',
      'PUBLIC_HIGH_SCORE_RESULT_WITH_SIGNATURE_AND_ATTACHMENT', 'SOURCE_ARCHIVED_WITH_GLOBAL_SEQUENCE',
      'ARCHIVED_RESULT_DOCUMENT_DOWNLOADED_AND_AUDITED',
      'UNIFIED_EXPORT_SUCCEEDED_AND_AUDITED', 'CHROMIUM_DIAGNOSTICS_CLEAN']
  }
  fs.writeFileSync(evidenceFile, `${JSON.stringify(evidence, null, 2)}\n`)
  socket.close()
  process.stdout.write(JSON.stringify({ pass: true, projectId, lowResultId: low.resultId,
    highResultId: high.resultId, exportTaskId: succeededExport.taskId }))
})().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`)
  process.exitCode = 1
})

function documentUrl(value) {
  return new URL(value)
}
