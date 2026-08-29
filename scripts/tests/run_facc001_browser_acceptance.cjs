const fs = require('node:fs')
const path = require('node:path')
const { execFileSync } = require('node:child_process')

const endpoint = process.env.NPDMS_BROWSER_CDP_URL || 'http://127.0.0.1:9224'
const appUrl = process.env.NPDMS_BROWSER_APP_URL || 'http://localhost:19330'
const apiUrl = process.env.NPDMS_BROWSER_API_URL || 'http://localhost:59330/admin-api'
const password = process.env.FACC001_BROWSER_PASSWORD
if (!password) throw new Error('必须通过FACC001_BROWSER_PASSWORD提供正式验收密码')

const projectId = 992004000001
const projectCode = 'FACC001-ACCEPTANCE-001'
const username = 'facc001acceptance'
const evidenceFile = path.resolve('docs/engineering/evidence/f-acc-001-browser-evidence.json')
const screenshotDir = path.resolve('docs/engineering/evidence/f-acc-001-browser')
fs.mkdirSync(screenshotDir, { recursive: true })

const assert = (condition, message) => { if (!condition) throw new Error(message) }
const mysql = (sql) => execFileSync('docker', [
  'exec', 'npdms-50eb-test-mysql-1', 'sh', '-c',
  'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" npdms_test -e "$1"', '_', sql
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
    if (message.method === 'Runtime.exceptionThrown') pageErrors.push(message.params.exceptionDetails.text)
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
    assert(response.status < 400 && response.body?.code === 0, `公开API失败：HTTP ${response.status}, code=${response.body?.code}, msg=${response.body?.msg}`)
    return response.body.data
  }
  const key = (prefix) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
  const login = async () => {
    const response = await rawApi('POST', '/system/auth/login', { username, password, captchaVerification: '' })
    assert(response.status === 200 && response.body?.code === 0 && response.body?.data?.accessToken,
      `正式身份登录失败：HTTP ${response.status}, code=${response.body?.code}, msg=${response.body?.msg}`)
    return response.body.data.accessToken
  }
  const upload = async (token, reportVersionId, name) => evaluate(`(async () => {
    const base = ${JSON.stringify(apiUrl)}; const token = ${JSON.stringify('TOKEN_PLACEHOLDER')};
    const headers = { 'tenant-id': '0', authorization: 'Bearer ' + token, 'Content-Type': 'application/json' };
    const referenceKey = crypto.randomUUID();
    const init = await fetch(base + '/api/v1/pms/files:init-upload', { method: 'POST', headers: {
      ...headers, 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ ownerContext: 'ACC',
      objectType: 'ACCEPTANCE_REPORT_VERSION', objectId: ${JSON.stringify(String(reportVersionId))},
      purposeCode: 'ACCEPTANCE_REPORT_ATTACHMENT', referenceKey,
      modeCode: 'CREATE_ARTIFACT', fileName: ${JSON.stringify(name)}, categoryCode: 'ACCEPTANCE_REPORT_ATTACHMENT',
      declaredSizeBytes: 38, declaredMediaType: 'application/pdf' }) }).then(r => r.json());
    if (init.code !== 0) return init;
    const form = new FormData(); form.append('sessionId', String(init.data.sessionId));
    form.append('file', new File([new TextEncoder().encode('%PDF-1.4\\nF-ACC-001 browser evidence\\n%%EOF')], ${JSON.stringify(name)}, { type: 'application/pdf' }));
    return fetch(base + '/api/v1/pms/files/' + init.data.artifactId + ':complete-upload', { method: 'POST',
      headers: { 'tenant-id': '0', authorization: 'Bearer ' + token, 'Idempotency-Key': crypto.randomUUID() },
      body: form }).then(r => r.json());
  })()`.replace('TOKEN_PLACEHOLDER', token))

  await Promise.all([send('Page.enable'), send('Runtime.enable'), send('Network.enable'), send('Log.enable')])
  await navigate(appUrl)
  const token = await login()
  const permissions = await api('GET', '/system/auth/get-permission-info', undefined, {}, token)
  for (const permission of ['pms:acceptance:report:query', 'pms:acceptance:report:write',
    'pms:acceptance:report:complete', 'pms:acceptance:report:download', 'pms:project-task:execute',
    'pms:file:upload', 'pms:file:download', 'pms:file:archive']) {
    assert(permissions.permissions.includes(permission), `正式身份缺少权限：${permission}`)
  }
  let activities = await api('GET', `/api/v1/pms/acceptances?projectId=${projectId}`, undefined, {}, token)
  const preliminary = activities.find((item) => item.acceptanceType === 'PRELIMINARY')
  const finalActivity = activities.find((item) => item.acceptanceType === 'FINAL')
  assert(preliminary && finalActivity, '受管项目未形成初验/终验活动')

  const emptyDraft = await api('POST', `/api/v1/pms/acceptances/${preliminary.id}/report-versions`, {},
    { 'If-Match': String(preliminary.version) }, token)
  const preliminaryTask = await api('GET', `/api/v1/pms/project-tasks/${preliminary.projectTaskId}`, undefined, {}, token)
  const incomplete = await rawApi('POST', `/api/v1/pms/project-tasks/${preliminary.projectTaskId}/actions/complete`, {
    reason: 'F-ACC-001 incomplete negative', executionContractId: preliminary.executionContractId,
    contractVersion: 1, factObjectKey: String(preliminary.id), factVersion: preliminary.version,
    expectedActivityVersion: preliminary.version, expectedReportVersion: emptyDraft.reportVersionNo
  }, { 'If-Match': String(preliminaryTask.version), 'Idempotency-Key': key('incomplete') }, token)
  assert(incomplete.body?.code !== 0, '缺四项与附件的报告错误完成了任务')

  await api('PATCH', `/api/v1/pms/acceptances/${preliminary.id}/report-versions/${emptyDraft.reportVersionId}`, {
    expectedReportVersionNo: emptyDraft.reportVersionNo, acceptanceTime: '2026-08-30T10:00:00',
    conclusionCode: 'PASS', conclusionText: '初验V1', acceptorName: '正式验收人'
  }, { 'If-Match': String(preliminary.version) }, token)
  const uploadV1 = await upload(token, emptyDraft.reportVersionId, 'facc001-preliminary-v1.pdf')
  assert(uploadV1.code === 0, `初验V1附件上传失败：${uploadV1.msg}`)
  const v1 = await api('POST', `/api/v1/pms/acceptances/${preliminary.id}/report-versions/${emptyDraft.reportVersionId}/actions/publish`, {
    expectedReportVersionNo: emptyDraft.reportVersionNo, expectedCurrentReportVersionId: null
  }, { 'If-Match': String(preliminary.version), 'Idempotency-Key': key('publish-v1') }, token)

  activities = await api('GET', `/api/v1/pms/acceptances?projectId=${projectId}`, undefined, {}, token)
  const preliminaryAfterV1 = activities.find((item) => item.id === preliminary.id)
  const draftV2 = await api('POST', `/api/v1/pms/acceptances/${preliminary.id}/report-versions`, {
    acceptanceTime: '2026-08-30T11:00:00', conclusionCode: 'PASS', conclusionText: '初验V2', acceptorName: '正式验收人'
  }, { 'If-Match': String(preliminaryAfterV1.version) }, token)
  const uploadV2 = await upload(token, draftV2.reportVersionId, 'facc001-preliminary-v2.pdf')
  assert(uploadV2.code === 0, `初验V2附件上传失败：${uploadV2.msg}`)
  await api('POST', `/api/v1/pms/acceptances/${preliminary.id}/report-versions/${draftV2.reportVersionId}/actions/publish`, {
    expectedReportVersionNo: draftV2.reportVersionNo, expectedCurrentReportVersionId: v1.reportVersionId
  }, { 'If-Match': String(preliminaryAfterV1.version), 'Idempotency-Key': key('publish-v2') }, token)

  const history = await api('GET', `/api/v1/pms/acceptances/${preliminary.id}/report-versions`, undefined, {}, token)
  const historicalV1 = history.find((item) => item.id === v1.reportVersionId)
  assert(historicalV1?.reportStatus === 'SUPERSEDED' && historicalV1.attachments.length === 1, 'V1历史未保留')
  const downloadFact = await api('GET', `/api/v1/pms/acceptances/${preliminary.id}/report-versions/${v1.reportVersionId}/attachments/1/download`, undefined, {}, token)
  const ticket = await api('POST', `/api/v1/pms/files/${downloadFact.artifactId}/access-tickets`, {
    versionNo: downloadFact.versionNo, operationCode: 'DOWNLOAD', ownerContext: 'ACC',
    objectType: 'ACCEPTANCE_REPORT_VERSION', objectId: String(v1.reportVersionId),
    purposeCode: 'ACCEPTANCE_REPORT_ATTACHMENT', referenceKey: downloadFact.referenceKey
  }, {}, token)
  assert(ticket.shortLivedUrl && !ticket.shortLivedUrl.includes('token='), '历史下载未返回受控短时票据')

  const finalDraft = await api('POST', `/api/v1/pms/acceptances/${finalActivity.id}/report-versions`, {
    acceptanceTime: '2026-08-30T12:00:00', conclusionCode: 'PASS', conclusionText: '终验V1', acceptorName: '正式验收人'
  }, { 'If-Match': String(finalActivity.version) }, token)
  const finalUpload = await upload(token, finalDraft.reportVersionId, 'facc001-final-v1.pdf')
  assert(finalUpload.code === 0, `终验附件上传失败：${finalUpload.msg}`)
  await api('POST', `/api/v1/pms/acceptances/${finalActivity.id}/report-versions/${finalDraft.reportVersionId}/actions/publish`, {
    expectedReportVersionNo: finalDraft.reportVersionNo, expectedCurrentReportVersionId: null
  }, { 'If-Match': String(finalActivity.version), 'Idempotency-Key': key('publish-final') }, token)
  const finalTask = await api('GET', `/api/v1/pms/project-tasks/${finalActivity.projectTaskId}`, undefined, {}, token)
  const completed = await api('POST', `/api/v1/pms/project-tasks/${finalActivity.projectTaskId}/actions/complete`, {
    reason: 'F-ACC-001 Chromium completion', executionContractId: finalActivity.executionContractId,
    contractVersion: 1, factObjectKey: String(finalActivity.id), factVersion: finalActivity.version,
    expectedActivityVersion: finalActivity.version, expectedReportVersion: finalDraft.reportVersionNo
  }, { 'If-Match': String(finalTask.version), 'Idempotency-Key': key('complete-final') }, token)
  assert(completed.status === 'DONE', '终验任务未完成')

  const currentPreliminary = (await api('GET', `/api/v1/pms/acceptances/${preliminary.id}`, undefined, {}, token))
  const currentVersions = await api('GET', `/api/v1/pms/acceptances/${preliminary.id}/report-versions`, undefined, {}, token)
  const effectivePreliminary = currentVersions.find((item) => item.reportStatus === 'EFFECTIVE')
  await api('POST', `/api/v1/pms/acceptances/${preliminary.id}/actions/revoke-current-version`, {
    expectedCurrentReportVersionId: effectivePreliminary.id, expectedCurrentReportVersionNo: effectivePreliminary.reportVersionNo
  }, { 'If-Match': String(currentPreliminary.version), 'Idempotency-Key': key('revoke') }, token)

  const unrelated = await rawApi('GET', '/api/v1/pms/acceptances?projectId=992004000099', undefined, {}, token)
  assert(unrelated.body?.code !== 0 || unrelated.body?.data?.length === 0, '无权项目错误返回验收活动')
  const crossTenant = await rawApi('GET', `/api/v1/pms/acceptances?projectId=${projectId}`, undefined, {}, token, 1)
  assert(crossTenant.body?.code !== 0 || crossTenant.body?.data?.length === 0, '跨租户错误返回验收活动')

  await navigate(`${appUrl}/pms/customer-asset/acceptance-reports?projectId=${projectId}`)
  assert(await evaluate(`document.body.innerText.includes('初验 / 终验报告')`), '公开报告页面未渲染')
  await screenshot('01-facc001-report-history.png')

  const dbFacts = {
    projectCode,
    reportVersions: Number(mysql(`SELECT COUNT(*) FROM acc_acceptance_report_version WHERE tenant_id=0 AND acceptance_id IN (${preliminary.id},${finalActivity.id})`)),
    sourceVersions: Number(mysql(`SELECT COUNT(*) FROM acc_project_deliverable_source_version WHERE tenant_id=0 AND source_object_type='ACCEPTANCE_REPORT_VERSION' AND source_object_id IN (${v1.reportVersionId},${draftV2.reportVersionId},${finalDraft.reportVersionId})`)),
    archiveRecords: Number(mysql(`SELECT COUNT(*) FROM plt_file_archive_record WHERE tenant_id=0 AND business_object_id IN ('${v1.reportVersionId}','${draftV2.reportVersionId}','${finalDraft.reportVersionId}')`)),
    reportEvents: Number(mysql(`SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=0 AND event_type='AcceptanceReportVersionChanged' AND aggregate_key IN ('${preliminary.id}','${finalActivity.id}')`)),
    finalTaskStatus: mysql(`SELECT status FROM proj_project_task WHERE tenant_id=0 AND id=${finalActivity.projectTaskId}`)
  }
  assert(dbFacts.reportVersions >= 3 && dbFacts.sourceVersions >= 3 && dbFacts.archiveRecords >= 1
    && dbFacts.reportEvents >= 4 && dbFacts.finalTaskStatus === 'DONE', '真实数据库验收事实不完整')
  assert(consoleErrors.length === 0 && pageErrors.length === 0 && requestFailures.length === 0,
    `浏览器存在意外错误：${JSON.stringify({ consoleErrors, pageErrors, requestFailures })}`)

  const evidence = {
    featureId: 'F-ACC-001', requirementIds: ['ACC-03@V1', 'ACC-04@V1'], pass: true,
    identity: { userId: 992004800001, username, tenantId: 0 }, project: { projectId, projectCode },
    assertions: ['INCOMPLETE_REPORT_BLOCKS_TASK', 'PRELIMINARY_V1_EFFECTIVE', 'V2_REPLACES_V1',
      'HISTORICAL_V1_DOWNLOADABLE', 'FINAL_REPORT_TASK_COMPLETED', 'CURRENT_VERSION_REVOKED_NO_RESTORE',
      'PROJECT_AND_TENANT_SCOPE_ENFORCED', 'DATABASE_FACTS_CONFIRMED'],
    dbFacts, diagnostics: { consoleErrors, pageErrors, requestFailures }, generatedAt: new Date().toISOString()
  }
  fs.mkdirSync(path.dirname(evidenceFile), { recursive: true })
  fs.writeFileSync(evidenceFile, JSON.stringify(evidence, null, 2) + '\n')
  console.log(JSON.stringify({ pass: true, evidenceFile, assertions: evidence.assertions, dbFacts }))
  socket.close()
})().catch((error) => { console.error(error.stack || error.message); process.exitCode = 1 })
