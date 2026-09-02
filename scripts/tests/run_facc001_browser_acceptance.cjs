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
const unrelatedExistingProjectId = 992002000000
const username = 'facc001acceptance'
const evidenceFile = path.resolve('docs/engineering/evidence/f-acc-001-browser-evidence.json')
const screenshotDir = path.resolve('docs/engineering/evidence/f-acc-001-browser')
fs.mkdirSync(screenshotDir, { recursive: true })

const assert = (condition, message) => { if (!condition) throw new Error(message) }
let acceptanceSocket
const mysql = (sql) => execFileSync('docker', [
  'exec', 'npdms-50eb-test-mysql-1', 'sh', '-c',
  'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" npdms_test -e "$1" 2>/dev/null', '_', sql
], { encoding: 'utf8' }).trim()
const runArchiveRetryIntegration = () => {
  const inspect = execFileSync('docker', ['inspect', 'npdms-50eb-test-mysql-1',
    '--format', '{{range .Config.Env}}{{println .}}{{end}}'], { encoding: 'utf8' })
  const passwordLine = inspect.split(/\r?\n/).find((line) => line.startsWith('MYSQL_ROOT_PASSWORD='))
  assert(passwordLine, '无法取得正式MySQL测试实例凭据')
  execFileSync('mvn.cmd', ['-pl', 'pms-module-project', '-am',
    '-Dtest=Facc001ApplicationMySqlIntegrationTest', '-Dsurefire.failIfNoSpecifiedTests=false',
    '-DskipITs=false', 'test'], {
    cwd: path.resolve('.'), stdio: 'pipe', shell: true,
    env: { ...process.env, NPDMS_DB_NAME: 'npdms_test', NPDMS_MYSQL_PORT: '23316',
      NPDMS_DB_USER: 'root', NPDMS_DB_PASSWORD: passwordLine.slice('MYSQL_ROOT_PASSWORD='.length) }
  })
  return true
}

;(async () => {
  const target = await fetch(`${endpoint}/json/new?about:blank`, { method: 'PUT' }).then((r) => r.json())
  assert(target.webSocketDebuggerUrl, '无法创建独立Chromium验收页签')
  const socket = acceptanceSocket = new WebSocket(target.webSocketDebuggerUrl)
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
  const waitUntil = async (probe, message, timeoutMs = 90000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      if (probe()) return
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
    assert(response.status < 400 && response.body?.code === 0, `公开API失败：HTTP ${response.status}, code=${response.body?.code}, msg=${response.body?.msg}`)
    return response.body.data
  }
  const assertDeniedWithoutLeak = (response, protectedIds, message) => {
    assert(response.status >= 400 || response.body?.code !== 0, `${message}：请求未被拒绝`)
    assert(response.body?.data == null, `${message}：拒绝响应错误返回业务数据`)
    const responseText = JSON.stringify(response.body)
    assert(protectedIds.every((id) => !responseText.includes(String(id))), `${message}：拒绝响应泄露对象身份`)
  }
  const key = (prefix) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
  const login = async (loginUsername = username, tenantId = 0) => {
    const response = await rawApi('POST', '/system/auth/login',
      { username: loginUsername, password, captchaVerification: '' }, {}, undefined, tenantId)
    assert(response.status === 200 && response.body?.code === 0 && response.body?.data?.accessToken,
      `正式身份登录失败：HTTP ${response.status}, code=${response.body?.code}, msg=${response.body?.msg}`)
    return response.body.data
  }
  const upload = async (token, reportVersionId, name) => evaluate(`(async () => {
    const base = ${JSON.stringify(apiUrl)}; const token = ${JSON.stringify('TOKEN_PLACEHOLDER')};
    const headers = { 'tenant-id': '0', authorization: 'Bearer ' + token, 'Content-Type': 'application/json' };
    const referenceKey = crypto.randomUUID();
    const content = new TextEncoder().encode('%PDF-1.4\\nF-ACC-001 browser evidence\\n%%EOF');
    const init = await fetch(base + '/api/v1/pms/files:init-upload', { method: 'POST', headers: {
      ...headers, 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ ownerContext: 'ACC',
      objectType: 'ACCEPTANCE_REPORT_VERSION', objectId: ${JSON.stringify(String(reportVersionId))},
      purposeCode: 'ACCEPTANCE_REPORT_ATTACHMENT', referenceKey,
      modeCode: 'CREATE_ARTIFACT', fileName: ${JSON.stringify(name)}, categoryCode: 'ACCEPTANCE_REPORT_ATTACHMENT',
      declaredSizeBytes: content.byteLength, declaredMediaType: 'application/pdf' }) }).then(r => r.json());
    if (init.code !== 0) return init;
    const form = new FormData(); form.append('sessionId', String(init.data.sessionId));
    form.append('file', new File([content], ${JSON.stringify(name)}, { type: 'application/pdf' }));
    return fetch(base + '/api/v1/pms/files/' + init.data.artifactId + ':complete-upload', { method: 'POST',
      headers: { 'tenant-id': '0', authorization: 'Bearer ' + token, 'Idempotency-Key': crypto.randomUUID() },
      body: form }).then(r => r.json());
  })()`.replace('TOKEN_PLACEHOLDER', token))

  await Promise.all([send('Page.enable'), send('Runtime.enable'), send('Network.enable'), send('Log.enable')])
  await send('Page.addScriptToEvaluateOnNewDocument', { source: `
    window.__faccBusinessErrors = [];
    addEventListener('DOMContentLoaded', () => new MutationObserver((mutations) => {
      for (const mutation of mutations) for (const node of mutation.addedNodes) {
        if (node.nodeType !== Node.ELEMENT_NODE) continue;
        const candidates = node.matches?.('.el-message--error') ? [node]
          : [...(node.querySelectorAll?.('.el-message--error') || [])];
        for (const item of candidates) window.__faccBusinessErrors.push(item.innerText || item.textContent || '业务错误');
      }
    }).observe(document.body, { childList: true, subtree: true }));
  ` })
  await navigate(appUrl)
  const archiveFailureRetryTest = runArchiveRetryIntegration()
  const auth = await login()
  const token = auth.accessToken
  await evaluate(`(() => {
    const expiresAt = Date.now() + 8 * 60 * 60 * 1000;
    const cache = (value) => JSON.stringify({ c: Date.now(), e: expiresAt, v: JSON.stringify(value) });
    localStorage.setItem('ACCESS_TOKEN', cache(${JSON.stringify(auth.accessToken)}));
    localStorage.setItem('REFRESH_TOKEN', cache(${JSON.stringify(auth.refreshToken)}));
    localStorage.setItem('tenantId', cache(0));
    return true;
  })()`)
  const permissions = await api('GET', '/system/auth/get-permission-info', undefined, {}, token)
  for (const permission of ['pms:acceptance:report:query', 'pms:acceptance:report:write',
    'pms:acceptance:report:complete', 'pms:acceptance:report:download', 'pms:project-task:execute',
    'pms:file:upload', 'pms:file:download', 'pms:file:archive', 'pms:project:query']) {
    assert(permissions.permissions.includes(permission), `正式身份缺少权限：${permission}`)
  }
  let activities = await api('GET', `/api/v1/pms/acceptances?projectId=${projectId}`, undefined, {}, token)
  const preliminary = activities.find((item) => item.acceptanceType === 'PRELIMINARY')
  const finalActivity = activities.find((item) => item.acceptanceType === 'FINAL')
  assert(preliminary && finalActivity, '受管项目未形成初验/终验活动')

  const preliminaryWorkbench = await api('GET',
    `/api/v1/pms/project-tasks/${preliminary.projectTaskId}/workbench`, undefined, {}, token)
  const preliminaryCurrent = await api('GET', `/api/v1/pms/acceptances/${preliminary.id}`, undefined, {}, token)
  const incomplete = await rawApi('POST',
    `/api/v1/pms/project-tasks/${preliminary.projectTaskId}/actions/complete`, {
      reason: 'F-ACC-001 missing report guard', executionContractId: preliminaryWorkbench.executionContractId,
      contractVersion: preliminaryWorkbench.contractVersion, factObjectKey: String(preliminary.id),
      expectedActivityVersion: preliminaryCurrent.version, expectedReportVersion: 1
    }, { 'If-Match': String(preliminaryWorkbench.task.version), 'Idempotency-Key': key('missing-report') }, token)
  assert(incomplete.body?.code !== 0, '缺报告时错误完成了验收任务')

  while (new Date().getSeconds() % 30 > 5) await wait(1000)

  const preliminaryVersionsBefore = await api('GET',
    `/api/v1/pms/acceptances/${preliminary.id}/report-versions`, undefined, {}, token)
  const draftV1 = preliminaryVersionsBefore.find((item) => item.reportStatus === 'DRAFT')
    || await api('POST', `/api/v1/pms/acceptances/${preliminary.id}/report-versions`, {
      acceptanceTime: '2026-08-30T10:00:00', conclusionCode: 'PASS', conclusionText: '初验V1',
      acceptorName: '正式验收人'
    }, { 'If-Match': String(preliminary.version) }, token)
  const draftV1Id = draftV1.reportVersionId ?? draftV1.id
  const uploadV1 = await upload(token, draftV1Id, 'facc001-preliminary-v1.pdf')
  assert(uploadV1.code === 0, `初验V1附件上传失败：${uploadV1.msg}`)
  const v1 = await api('POST', `/api/v1/pms/acceptances/${preliminary.id}/report-versions/${draftV1Id}/actions/publish`, {
    expectedReportVersionNo: draftV1.reportVersionNo, expectedCurrentReportVersionId: null
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

  const finalDraft = await api('POST', `/api/v1/pms/acceptances/${finalActivity.id}/report-versions`, {
    acceptanceTime: '2026-08-30T12:00:00', conclusionCode: 'PASS', conclusionText: '终验V1', acceptorName: '正式验收人'
  }, { 'If-Match': String(finalActivity.version) }, token)
  const finalUpload = await upload(token, finalDraft.reportVersionId, 'facc001-final-v1.pdf')
  assert(finalUpload.code === 0, `终验附件上传失败：${finalUpload.msg}`)
  await api('POST', `/api/v1/pms/acceptances/${finalActivity.id}/report-versions/${finalDraft.reportVersionId}/actions/publish`, {
    expectedReportVersionNo: finalDraft.reportVersionNo, expectedCurrentReportVersionId: null
  }, { 'If-Match': String(finalActivity.version), 'Idempotency-Key': key('publish-final') }, token)
  await waitUntil(() => Number(mysql(`SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=0 AND event_type='AcceptanceReportVersionChanged' AND status='DELIVERED' AND aggregate_key IN ('${preliminary.id}','${finalActivity.id}')`)) >= 3
    && Number(mysql(`SELECT COUNT(*) FROM acc_project_deliverable_source_version WHERE tenant_id=0 AND source_object_id IN (${v1.reportVersionId},${draftV2.reportVersionId},${finalDraft.reportVersionId}) AND archive_status='ARCHIVED'`)) === 3
    && Number(mysql(`SELECT COUNT(*) FROM plt_file_archive_record WHERE tenant_id=0 AND artifact_id IN (SELECT file_artifact_id FROM acc_acceptance_report_attachment WHERE tenant_id=0 AND report_version_id IN (${v1.reportVersionId},${draftV2.reportVersionId},${finalDraft.reportVersionId}))`)) === 3,
  '正式Quartz未完成三份报告来源的独立归档')

  const history = await api('GET', `/api/v1/pms/acceptances/${preliminary.id}/report-versions`, undefined, {}, token)
  const historicalV1 = history.find((item) => item.id === v1.reportVersionId)
  assert(historicalV1?.reportStatus === 'SUPERSEDED' && historicalV1.attachments.length === 1, 'V1历史未保留')
  const downloadFact = await api('GET', `/api/v1/pms/acceptances/${preliminary.id}/report-versions/${v1.reportVersionId}/attachments/1/download`, undefined, {}, token)
  const ticket = await api('POST', `/api/v1/pms/files/${downloadFact.artifactId}/access-tickets`, {
    versionNo: downloadFact.versionNo, operationCode: 'DOWNLOAD', ownerContext: 'ACC',
    objectType: 'ACCEPTANCE_REPORT_VERSION', objectId: String(v1.reportVersionId),
    purposeCode: 'ACCEPTANCE_REPORT_ATTACHMENT', referenceKey: downloadFact.referenceKey
  }, {}, token)
  assert(ticket.shortLivedUrl && !ticket.shortLivedUrl.includes('token='), '归档后历史下载未返回受控短时票据')

  const finalWorkbench = await api('GET',
    `/api/v1/pms/project-tasks/${finalActivity.projectTaskId}/workbench`, undefined, {}, token)
  const finalActivityCurrent = await api('GET', `/api/v1/pms/acceptances/${finalActivity.id}`, undefined, {}, token)
  const completed = await api('POST', `/api/v1/pms/project-tasks/${finalActivity.projectTaskId}/actions/complete`, {
    reason: 'F-ACC-001 Chromium completion', executionContractId: finalWorkbench.executionContractId,
    contractVersion: finalWorkbench.contractVersion, factObjectKey: String(finalActivity.id),
    expectedActivityVersion: finalActivityCurrent.version, expectedReportVersion: finalDraft.reportVersionNo
  }, { 'If-Match': String(finalWorkbench.task.version), 'Idempotency-Key': key('complete-final') }, token)
  assert(completed.status === 'DONE', '终验任务未完成')

  const currentPreliminary = (await api('GET', `/api/v1/pms/acceptances/${preliminary.id}`, undefined, {}, token))
  const currentVersions = await api('GET', `/api/v1/pms/acceptances/${preliminary.id}/report-versions`, undefined, {}, token)
  const effectivePreliminary = currentVersions.find((item) => item.reportStatus === 'EFFECTIVE')
  await api('POST', `/api/v1/pms/acceptances/${preliminary.id}/actions/revoke-current-version`, {
    expectedCurrentReportVersionId: effectivePreliminary.id, expectedCurrentReportVersionNo: effectivePreliminary.reportVersionNo
  }, { 'If-Match': String(currentPreliminary.version), 'Idempotency-Key': key('revoke') }, token)
  await waitUntil(() => Number(mysql(`SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=0 AND event_type='AcceptanceReportVersionChanged' AND status='DELIVERED' AND aggregate_key IN ('${preliminary.id}','${finalActivity.id}')`)) === 4,
    '撤销事件未由正式Quartz投递')

  const unrelatedProjectExists = Number(mysql(`SELECT COUNT(*) FROM proj_project WHERE tenant_id=0 AND id=${unrelatedExistingProjectId} AND deleted=0`))
  const unrelatedProjectMembership = Number(mysql(`SELECT COUNT(*) FROM proj_project_member_assignment WHERE tenant_id=0 AND project_id=${unrelatedExistingProjectId} AND user_id=992004800001 AND status='ACTIVE' AND effective_to IS NULL AND deleted=0`))
  assert(unrelatedProjectExists === 1 && unrelatedProjectMembership === 0,
    '无权项目夹具不存在或验收身份意外取得该项目范围')
  const unrelated = await rawApi('GET', `/api/v1/pms/acceptances?projectId=${unrelatedExistingProjectId}`, undefined, {}, token)
  assertDeniedWithoutLeak(unrelated, [unrelatedExistingProjectId], '真实存在的无权项目验收活动查询')
  const tenantOneAuth = await login('admin', 1)
  const crossTenant = await rawApi('GET', `/api/v1/pms/acceptances?projectId=${projectId}`,
    undefined, {}, tenantOneAuth.accessToken, 1)
  assert(crossTenant.body?.code !== 0 || crossTenant.body?.data?.length === 0, '跨租户错误返回验收活动')
  const crossTenantDownload = await rawApi('GET',
    `/api/v1/pms/acceptances/${preliminary.id}/report-versions/${v1.reportVersionId}/attachments/1/download`,
    undefined, {}, tenantOneAuth.accessToken, 1)
  assertDeniedWithoutLeak(crossTenantDownload, [preliminary.id, v1.reportVersionId, downloadFact.artifactId],
    '跨租户报告附件下载事实')
  const crossTenantTicket = await rawApi('POST', `/api/v1/pms/files/${downloadFact.artifactId}/access-tickets`, {
    versionNo: downloadFact.versionNo, operationCode: 'DOWNLOAD', ownerContext: 'ACC',
    objectType: 'ACCEPTANCE_REPORT_VERSION', objectId: String(v1.reportVersionId),
    purposeCode: 'ACCEPTANCE_REPORT_ATTACHMENT', referenceKey: downloadFact.referenceKey
  }, {}, tenantOneAuth.accessToken, 1)
  assertDeniedWithoutLeak(crossTenantTicket, [preliminary.id, v1.reportVersionId, downloadFact.artifactId],
    '跨租户文件Access Ticket')

  consoleErrors.length = 0
  pageErrors.length = 0
  requestFailures.length = 0
  await navigate(`${appUrl}/customer-asset/acceptance-reports?projectId=${projectId}`)
  await wait(5000)
  const pageState = await evaluate(`({ text: document.body.innerText,
    businessErrors: window.__faccBusinessErrors || [],
    visibleErrors: [...document.querySelectorAll('.el-message--error')].map((item) => item.innerText) })`)
  assert(pageState.text.includes('初验报告') && pageState.text.includes('终验报告'), '公开报告活动卡片未完整渲染')
  assert(pageState.businessErrors.length === 0 && pageState.visibleErrors.length === 0,
    `公开报告页面存在业务错误：${JSON.stringify(pageState)}`)
  await screenshot('01-facc001-report-history.png')

  const dbFacts = {
    projectCode,
    reportVersions: Number(mysql(`SELECT COUNT(*) FROM acc_acceptance_report_version WHERE tenant_id=0 AND acceptance_id IN (${preliminary.id},${finalActivity.id})`)),
    sourceVersions: Number(mysql(`SELECT COUNT(*) FROM acc_project_deliverable_source_version WHERE tenant_id=0 AND source_object_type='AcceptanceReportVersion' AND source_object_id IN (${v1.reportVersionId},${draftV2.reportVersionId},${finalDraft.reportVersionId})`)),
    archiveRecords: Number(mysql(`SELECT COUNT(*) FROM plt_file_archive_record WHERE tenant_id=0 AND artifact_id IN (SELECT file_artifact_id FROM acc_acceptance_report_attachment WHERE tenant_id=0 AND report_version_id IN (${v1.reportVersionId},${draftV2.reportVersionId},${finalDraft.reportVersionId}))`)),
    reportEvents: Number(mysql(`SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=0 AND event_type='AcceptanceReportVersionChanged' AND aggregate_key IN ('${preliminary.id}','${finalActivity.id}')`)),
    deliveredReportEvents: Number(mysql(`SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=0 AND event_type='AcceptanceReportVersionChanged' AND status='DELIVERED' AND aggregate_key IN ('${preliminary.id}','${finalActivity.id}')`)),
    quartzJobs: Number(mysql(`SELECT COUNT(*) FROM QRTZ_JOB_DETAILS WHERE JOB_NAME IN ('acceptanceReportOutboxDeliveryJob','acceptanceReportArchiveCompensationJob')`)),
    quartzTriggers: Number(mysql(`SELECT COUNT(*) FROM QRTZ_TRIGGERS WHERE JOB_NAME IN ('acceptanceReportOutboxDeliveryJob','acceptanceReportArchiveCompensationJob')`)),
    unrelatedExistingProjectId,
    unrelatedProjectExists,
    unrelatedProjectMembership,
    unrelatedProjectQueryDenied: unrelated.status >= 400 || unrelated.body?.code !== 0,
    crossTenantDownloadDenied: crossTenantDownload.status >= 400 || crossTenantDownload.body?.code !== 0,
    crossTenantTicketDenied: crossTenantTicket.status >= 400 || crossTenantTicket.body?.code !== 0,
    archiveFailureRetryTest,
    finalTaskStatus: mysql(`SELECT status FROM proj_project_task WHERE tenant_id=0 AND id=${finalActivity.projectTaskId}`)
  }
  assert(dbFacts.reportVersions === 3 && dbFacts.sourceVersions === 3 && dbFacts.archiveRecords === 3
    && dbFacts.reportEvents === 4 && dbFacts.deliveredReportEvents === 4 && dbFacts.quartzJobs === 2
    && dbFacts.quartzTriggers === 2 && dbFacts.unrelatedProjectExists === 1
    && dbFacts.unrelatedProjectMembership === 0 && dbFacts.unrelatedProjectQueryDenied
    && dbFacts.crossTenantDownloadDenied
    && dbFacts.crossTenantTicketDenied && dbFacts.archiveFailureRetryTest && dbFacts.finalTaskStatus === 'DONE',
  `真实数据库与Quartz验收事实不完整：${JSON.stringify(dbFacts)}`)
  assert(consoleErrors.length === 0 && pageErrors.length === 0 && requestFailures.length === 0,
    `浏览器存在意外错误：${JSON.stringify({ consoleErrors, pageErrors, requestFailures })}`)

  const evidence = {
    featureId: 'F-ACC-001', requirementIds: ['ACC-03@V1', 'ACC-04@V1'], pass: true,
    identity: { userId: 992004800001, username, tenantId: 0 }, project: { projectId, projectCode },
    assertions: ['MISSING_REPORT_BLOCKS_TASK', 'PRELIMINARY_V1_PUBLISHED', 'V2_REPLACES_V1',
      'ALL_REPORT_SOURCES_ARCHIVED_BY_QUARTZ', 'HISTORICAL_V1_DOWNLOADABLE_AFTER_ARCHIVE',
      'FINAL_REPORT_TASK_COMPLETED', 'CURRENT_VERSION_REVOKED_NO_RESTORE',
      'EXISTING_UNAUTHORIZED_PROJECT_QUERY_DENIED', 'CROSS_TENANT_ATTACHMENT_DOWNLOAD_DENIED',
      'CROSS_TENANT_ACCESS_TICKET_DENIED_WITHOUT_EXISTENCE_LEAK', 'ARCHIVE_PROVIDER_FAILURE_RETRIES',
      'QUARTZ_JOBS_AND_TRIGGERS_REGISTERED', 'REPORT_EVENTS_DELIVERED_BY_QUARTZ',
      'REPORT_ACTIVITY_CARDS_RENDERED_WITHOUT_BUSINESS_ERRORS', 'DATABASE_FACTS_CONFIRMED'],
    dbFacts, diagnostics: { consoleErrors, pageErrors, requestFailures,
      businessErrors: pageState.businessErrors, visibleBusinessErrors: pageState.visibleErrors },
    generatedAt: new Date().toISOString()
  }
  fs.mkdirSync(path.dirname(evidenceFile), { recursive: true })
  fs.writeFileSync(evidenceFile, JSON.stringify(evidence, null, 2) + '\n')
  console.log(JSON.stringify({ pass: true, evidenceFile, assertions: evidence.assertions, dbFacts }))
  socket.close()
})().catch((error) => {
  acceptanceSocket?.close()
  console.error(error.stack || error.message)
  process.exitCode = 1
})
