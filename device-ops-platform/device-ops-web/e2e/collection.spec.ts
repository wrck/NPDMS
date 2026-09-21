import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { expect, test, type Page } from '@playwright/test'

const authority = 'https://idp.acceptance.example/realms/device-ops'
const clientId = 'device-ops-web'
const oidcStorageKey = `oidc.user:${authority}:${clientId}`

interface SafeSubmission {
  body: ProjectSubmissionBody | GenericSubmissionBody
  credentialPresent: boolean
  requestPath: string
  idempotencyKey: string
}

interface SubmittedTarget {
  password?: unknown
  privateKey?: unknown
  passphrase?: unknown
  project: Record<string, unknown>
  device: Record<string, unknown>
  extensions: Record<string, unknown>
  host: string
  port: number
  username: string
  hostKeyFingerprint: string
}

interface ProjectSubmissionBody {
  namespace: string
  project: Record<string, unknown>
  targets: SubmittedTarget[]
  activityType: string
  script: {
    source: string
    key: string
    version: string
    sha256: string
    parserType: string
    content: string
    policy: string
    parserConfig?: string
  }
}

interface GenericSubmissionBody {
  namespace: string
  connection: Omit<SubmittedTarget, 'project' | 'device' | 'extensions'>
  activityType: string
  script: ProjectSubmissionBody['script']
}

function semanticResults(collectionId: string, targetId: number) {
  const coordinate = {
    logType: 'device-command-output',
    releaseVersion: '1.3.0',
    engineVersion: '1.3.0',
    ruleVersion: '1.3.0',
    projectionVersion: '1.3.0'
  }
  const sections = [
    {
      sectionIndex: 1, type: 'keyValueTree', startLine: 1, endLine: 2,
      rawLines: ['Device:', '  Model: CR19000'], warnings: [], data: { Device: { Model: 'CR19000' } },
      entries: [{ key: 'Device', value: '', startLine: 1, endLine: 2, children: [
        { key: 'Model', value: 'CR19000', startLine: 2, endLine: 2, children: [] }
      ] }]
    },
    {
      sectionIndex: 2, type: 'table', startLine: 3, endLine: 4,
      rawLines: ['Interface Status', 'GE1/0/1 up'], warnings: [],
      columns: [{ id: 'interface', label: 'Interface', index: 0 }, { id: 'status', label: 'Status', index: 1 }],
      rows: [{ rowIndex: 1, values: { interface: 'GE1/0/1', status: 'up' } }], unparsedLines: []
    },
    {
      sectionIndex: 3, type: 'recordList', startLine: 5, endLine: 6, rawLines: [], warnings: [],
      records: [{ recordIndex: 1, identity: 'user admin', startLine: 5, endLine: 6,
        entries: [{ key: 'Role', value: 'operator', startLine: 6, endLine: 6, children: [] }], sections: [] }]
    },
    {
      sectionIndex: 4, type: 'configStanza', startLine: 7, endLine: 8, rawLines: [], warnings: [],
      stanzas: [{ header: 'interface GigabitEthernet1/0/1', startLine: 7, endLine: 8,
        lines: ['interface GigabitEthernet1/0/1', ' description uplink'] }]
    },
    {
      sectionIndex: 5, type: 'text', startLine: 9, endLine: 9,
      rawLines: ['diagnostic text'], warnings: [], lines: [{ lineNumber: 9, value: 'diagnostic text' }]
    }
  ]
  const semanticResult = {
    schemaVersion: '1.1.0', parserVersion: '1.3.0', ruleVersion: '1.3.0', projectionVersion: '1.3.0',
    snapshot: {}, projections: {}, quality: {},
    observations: [{ commandIndex: 1, status: 'UNPARSED', confidence: 0, matchedRuleIds: [], warnings: [] }],
    nestedObservations: [{ parentCommandIndex: 1, sectionIndex: 1, nestingDepth: 1,
      commandText: 'show session statistic', status: 'NO_DATA', confidence: 1,
      sourceLineStart: 10, sourceLineEnd: 10, matchedRuleIds: [], warnings: [] }],
    genericContent: { units: [
      { commandIndex: 1, parentCommandIndex: null, sectionIndex: null, nestingDepth: 0,
        commandText: 'show version', sourceLineStart: 1, sourceLineEnd: 9,
        structureStatus: 'STRUCTURED', sections, warnings: [] },
      { commandIndex: 2, parentCommandIndex: 1, sectionIndex: 1, nestingDepth: 1,
        commandText: 'show session statistic', sourceLineStart: 10, sourceLineEnd: 10,
        structureStatus: 'EMPTY', sections: [], warnings: [] }
    ] }
  }
  return [{
    targetId, taskId: `task-${collectionId}`, state: 'SUCCEEDED', releaseId: 'device-command-output-1.3.0',
    coordinate,
    result: {
      resultId: `result-${collectionId}`, taskId: `task-${collectionId}`,
      releaseId: 'device-command-output-1.3.0', coordinate, contextSnapshot: {}, semanticResult,
      createdAt: '2026-08-29T00:00:00Z'
    }
  }]
}

function formItem(page: Page, label: string) {
  return page.locator('.el-form-item').filter({ hasText: label }).first()
}

async function chooseSelect(page: Page, label: string, option: RegExp) {
  await formItem(page, label).locator('.el-select').click()
  await page.getByRole('option', { name: option }).click()
}

test('completes registered and direct collection journeys without leaking credentials', async ({
  context,
  page
}) => {
  const consoleProblems: string[] = []
  const pageErrors: string[] = []
  const failedRequests: string[] = []
  const safeSubmissions: SafeSubmission[] = []
  const pollCounts = new Map<string, number>()
  let masterRevision = 1

  page.on('console', (message) => {
    if (message.type() === 'error' || message.type() === 'warning') {
      consoleProblems.push(`${message.type()}: ${message.text()}`)
    }
  })
  page.on('pageerror', (error) => pageErrors.push(error.message))
  page.on('requestfailed', (request) => {
    failedRequests.push(`${request.method()} ${new URL(request.url()).pathname}`)
  })

  const oidcUser = JSON.stringify({
    id_token: 'test-id-token',
    access_token: 'test-access-token',
    token_type: 'Bearer',
    scope: 'openid profile device-ops:collections:read device-ops:collections:execute',
    profile: {
      sub: 'browser-acceptance-user',
      iss: authority,
      aud: clientId,
      exp: Math.floor(Date.now() / 1000) + 3600
    },
    expires_at: Math.floor(Date.now() / 1000) + 3600
  })
  await context.addInitScript(
    ({ key, value }) => {
      if (window.location.origin === 'http://127.0.0.1:15174') {
        window.sessionStorage.setItem(key, value)
      }
    },
    { key: oidcStorageKey, value: oidcUser }
  )

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (path === '/api/v1/runtime-config') {
      await route.fulfill({
        json: {
          oidcAuthority: authority,
          oidcClientId: clientId,
          oidcScope:
            'openid profile device-ops:projects:read device-ops:devices:read device-ops:collections:execute device-ops:collections:read',
          apiBaseUrl: '/api/v1'
        }
      })
      return
    }

    if (path === '/api/v1/master-data/projects') {
      await route.fulfill({
        json: [
          {
            namespace: 'npdp',
            projectKey: 'PROJ-ACCEPT',
            projectName: masterRevision === 1 ? '验收项目' : '上游已变更项目',
            projectCode: 'ACCEPT-001'
          }
        ]
      })
      return
    }

    if (path === '/api/v1/master-data/projects/PROJ-ACCEPT/devices') {
      await route.fulfill({
        json: [
          {
            deviceKey: 'DEVICE-ACCEPT-01',
            deviceName: masterRevision === 1 ? '核心交换机' : '上游已变更设备',
            vendor: masterRevision === 1 ? 'Huawei' : 'ChangedVendor',
            model: masterRevision === 1 ? 'S6730' : 'ChangedModel'
          }
        ]
      })
      return
    }

    if (path === '/api/v1/parser-options' && request.method() === 'GET') {
      const coordinate = {
        logType: 'device-command-output', releaseVersion: '1.3.0', engineVersion: '1.3.0',
        ruleVersion: '1.3.0', projectionVersion: '1.3.0'
      }
      await route.fulfill({ json: {
        automaticEnabled: true,
        defaultAvailable: true,
        defaultReleaseId: 'device-command-output-1.3.0',
        options: [{ releaseId: 'device-command-output-1.3.0', logType: 'device-command-output',
          displayName: '通用设备命令输出', releaseVersion: '1.3.0', coordinate, activeDefault: true }]
      } })
      return
    }

    if (path === '/api/v1/saved-connections' && request.method() === 'GET') {
      await route.fulfill({ json: { items: [] } })
      return
    }

    if (path.endsWith('/output-events') && request.method() === 'GET') {
      await route.fulfill({
        contentType: 'text/event-stream',
        body: 'event: complete\ndata: {}\n\n'
      })
      return
    }

    const evidenceMatch = path.match(/^\/api\/v1\/(?:projects\/PROJ-ACCEPT\/)?collections\/(collection-\d+)\/evidence$/)
    if (evidenceMatch?.[1] && request.method() === 'GET') {
      const index = Number(evidenceMatch[1].split('-')[1]) - 1
      const saved = safeSubmissions[index]!
      const body = saved.body
      const target = 'targets' in body ? body.targets[0]! : body.connection
      const { content, ...scriptMetadata } = body.script
      delete scriptMetadata.parserConfig
      const snapshotBody = { ...body, script: scriptMetadata }
      await route.fulfill({ headers: { 'Cache-Control': 'no-store' }, json: {
        metadata: { collectionId: evidenceMatch[1], namespace: body.namespace,
          projectKey: 'targets' in body ? 'PROJ-ACCEPT' : null, externalRequestId: null,
          activityType: body.activityType, createdAt: '2026-09-08T00:00:00Z' },
        input: { ...scriptMetadata, contentStatus: 'AVAILABLE', content },
        submission: { provenance: 'CAPTURED_SUBMISSION',
          snapshot: { schemaVersion: 1, request: { method: 'POST', path: saved.requestPath, idempotencyKey: saved.idempotencyKey }, body: snapshotBody },
          omittedFields: ['body.script.content', 'body.script.parserConfig', 'targets' in body ? 'body.targets[0].password' : 'body.connection.password'] },
        executionFacts: { targets: [{ targetId: index + 1, deviceKey: 'device' in target ? target.device.deviceKey : null,
          protocol: 'SSH2', host: target.host, port: target.port, username: target.username,
          hostKeyFingerprint: target.hostKeyFingerprint, status: 'SUCCEEDED' }], semanticParsing: {
          logType: 'device-command-output', releaseId: 'device-command-output-1.3.0', inputFormat: 'COMMAND_BLOCKS', resultConsumerId: null
        } }
      } })
      return
    }

    const semanticMatch = path.match(/^\/api\/v1\/collections\/(collection-\d+)\/semantic-results$/)
    if (semanticMatch?.[1] && request.method() === 'GET') {
      const targetId = Number(semanticMatch[1].split('-')[1])
      await route.fulfill({ json: semanticResults(semanticMatch[1], targetId) })
      return
    }

    const projectCollectionMatch = path.match(
      /^\/api\/v1\/projects\/PROJ-ACCEPT\/collections(?:\/([^/]+))?$/
    )
    if (projectCollectionMatch && request.method() === 'POST') {
      const body = request.postDataJSON() as ProjectSubmissionBody
      const target = body.targets?.[0]
      const credentialPresent = Boolean(target?.password || target?.privateKey)
      const safeBody = structuredClone(body)
      for (const safeTarget of safeBody.targets ?? []) {
        delete safeTarget.password
        delete safeTarget.privateKey
        delete safeTarget.passphrase
      }
      safeSubmissions.push({ body: safeBody, credentialPresent, requestPath: path, idempotencyKey: request.headers()['idempotency-key'] ?? '' })
      masterRevision = 2
      await route.fulfill({
        status: 202,
        json: {
          collectionId: `collection-${safeSubmissions.length}`,
          existing: false
        }
      })
      return
    }

    const genericCollectionMatch = path.match(/^\/api\/v1\/collections(?:\/([^/]+))?$/)
    if (genericCollectionMatch && !genericCollectionMatch[1] && request.method() === 'POST') {
      const body = request.postDataJSON() as GenericSubmissionBody
      const credentialPresent = Boolean(body.connection.password || body.connection.privateKey)
      const safeBody = structuredClone(body)
      delete safeBody.connection.password
      delete safeBody.connection.privateKey
      delete safeBody.connection.passphrase
      safeSubmissions.push({ body: safeBody, credentialPresent, requestPath: path, idempotencyKey: request.headers()['idempotency-key'] ?? '' })
      await route.fulfill({
        status: 202,
        json: { collectionId: `collection-${safeSubmissions.length}`, existing: false }
      })
      return
    }

    const collectionId = projectCollectionMatch?.[1] ?? genericCollectionMatch?.[1]
    if (collectionId && request.method() === 'GET') {
      const pollCount = (pollCounts.get(collectionId) ?? 0) + 1
      pollCounts.set(collectionId, pollCount)
      const submissionIndex = Number(collectionId.split('-')[1]) - 1
      const submission = safeSubmissions[submissionIndex]?.body
      const terminal = pollCount >= 2
      const target = 'targets' in submission ? submission.targets[0] : submission.connection
      await route.fulfill({
        json: {
          collectionId,
          namespace: submission.namespace,
          ...('project' in submission ? { projectKey: 'PROJ-ACCEPT' } : {}),
          activityType: submission.activityType,
          status: terminal ? 'SUCCEEDED' : 'EXECUTING',
          script: {
            source: submission.script.source,
            key: submission.script.key,
            version: submission.script.version,
            sha256: submission.script.sha256,
            parserType: submission.script.parserType
          },
          targets: [
            {
              targetId: submissionIndex + 1,
              contextSnapshot: {
                project: 'project' in target ? target.project : {},
                device: 'device' in target ? target.device : {},
                extensions: 'extensions' in target ? target.extensions : {}
              },
              endpointSnapshot: {
                host: target.host,
                port: target.port,
                username: target.username,
                hostKeyFingerprint: target.hostKeyFingerprint
              },
              status: terminal ? 'SUCCEEDED' : 'EXECUTING',
              stdout: terminal ? 'hostname=core-accept-01\\nvendor=Huawei\\nmodel=S6730' : '',
              stderr: terminal ? 'notice: collection completed with cached data' : '',
              exitCode: terminal ? 0 : null,
              truncated: false,
              parsedFacts: terminal ? { vendor: 'Huawei', model: 'S6730' } : {},
              outcome: null,
              commandBlocks: terminal ? [{
                commandIndex: 1,
                commandText: 'show version',
                status: 'SUCCEEDED',
                stdout: 'hostname=core-accept-01\\nvendor=Huawei\\nmodel=S6730',
                stderr: 'notice: collection completed with cached data',
                receivedBytes: 96,
                pageCount: 0,
                truncated: false,
                exitCode: 0,
                parsedFacts: { vendor: 'Huawei', model: 'S6730' },
                parseWarnings: [],
                legacy: false
              }] : []
            }
          ]
        }
      })
      return
    }

    await route.fulfill({ status: 404, json: { message: 'acceptance route not found' } })
  })

  await page.goto('/projects/PROJ-ACCEPT')
  await expect(page.getByRole('heading', { name: '设备连接与采集工作台' })).toBeVisible()
  await expect(
    page.getByRole('button', { name: /新增|新建|编辑|导入|保存项目|保存设备|保存凭据/ })
  ).toHaveCount(0)

  await page.getByRole('tab', { name: '项目设备' }).click()
  await chooseSelect(page, '设备（可选，只读代理）', /核心交换机/)
  await formItem(page, 'IP / 主机名').locator('input').fill('10.20.30.40')
  await formItem(page, '用户名').locator('input').fill('accept-ops')
  await page.getByText('高级连接设置', { exact: true }).click()
  await formItem(page, '主机密钥指纹（可选）')
    .locator('input')
    .fill('SHA256:acceptance-host-fingerprint')
  const registeredPassword = page.getByRole('textbox', { name: '密码', exact: true })
  await registeredPassword.fill('registered-one-time-secret')

  await page.locator('.script-editor__source .el-select').click()
  await page.getByRole('option', { name: '外部系统交付' }).click()
  await page.getByText('制品', { exact: true }).click()
  await page.getByText('脚本标识', { exact: true }).locator('..').locator('input').fill('inventory-acceptance')
  await page.getByText('不可变版本', { exact: true }).locator('..').locator('input').fill('2026.07.29')
  await page.getByText('兼容解析配置', { exact: true }).click()
  await chooseSelect(page, '命令内联解析器', /键值对/)
  const scriptContent = 'display version\\ndisplay device'
  await page.getByRole('textbox', { name: '采集命令内容' }).fill(scriptContent)
  const expectedSha = createHash('sha256').update(scriptContent).digest('hex')
  await expect(page.locator('.artifact-identity__hash dd')).toHaveText(expectedSha)

  await page.getByRole('button', { name: '连接并执行采集' }).click()
  await expect.poll(() => safeSubmissions.length).toBe(1)
  const registered = safeSubmissions[0]
  const registeredBody = registered.body as ProjectSubmissionBody
  expect(registered.credentialPresent).toBe(true)
  expect(registeredBody.project).toMatchObject({
    namespace: 'npdp',
    projectKey: 'PROJ-ACCEPT',
    projectName: '验收项目',
    projectCode: 'ACCEPT-001'
  })
  expect(registeredBody.targets[0]).toMatchObject({
    device: {
      deviceKey: 'DEVICE-ACCEPT-01',
      deviceName: '核心交换机',
      vendor: 'Huawei',
      model: 'S6730'
    },
    host: '10.20.30.40',
    username: 'accept-ops',
    hostKeyFingerprint: 'SHA256:acceptance-host-fingerprint'
  })
  expect(registeredBody.script).toMatchObject({
    source: 'EXTERNAL_DELIVERED',
    key: 'inventory-acceptance',
    version: '2026.07.29',
    sha256: expectedSha,
    parserType: 'KEY_VALUE'
  })
  expect(JSON.stringify(registeredBody)).not.toContain('registered-one-time-secret')
  await expect(page.getByText('采集 ID / collection-1')).toBeVisible()

  const upstreamDevice = await page.evaluate(async () => {
    const response = await fetch(
      '/api/v1/master-data/projects/PROJ-ACCEPT/devices?query=changed'
    )
    return (await response.json())[0]
  })
  expect(upstreamDevice.deviceName).toBe('上游已变更设备')

  await expect(page.locator('.task-panel__state').getByText('采集成功', { exact: true })).toBeVisible({
    timeout: 10_000
  })
  const stdoutText = page.getByText('hostname=core-accept-01', { exact: false })
  if (!await stdoutText.isVisible()) {
    await page.locator('.task-output-tabs .el-tab-pane:visible .command-output-blocks .el-collapse-item__header').click()
  }
  await expect(stdoutText).toBeVisible()
  await page.getByRole('tab', { name: '错误输出' }).click()
  const stderrText = page.getByText('notice: collection completed with cached data')
  if (!await stderrText.isVisible()) {
    await page.locator('.task-output-tabs .el-tab-pane:visible .command-output-blocks .el-collapse-item__header').click()
  }
  await expect(stderrText).toBeVisible()
  await page.getByRole('tab', { name: '解析事实' }).click()
  const facts = page.locator('.facts')
  await expect(facts).toContainText('vendor')
  await expect(facts).toContainText('Huawei')
  await expect(facts).toContainText('model')
  await expect(facts).toContainText('S6730')
  expect(pageErrors).toEqual([])
  expect(consoleProblems).toEqual([])

  const semanticHeader = page.locator('.semantic-result > .el-collapse .el-collapse-item__header').first()
  await expect(semanticHeader).toHaveAttribute('aria-expanded', 'true')
  await page.getByRole('tab', { name: '通用结构' }).click()
  const genericStructure = page.locator('.generic-structure')
  await genericStructure.getByRole('button', { name: /show version 已结构化/ }).click()
  await expect(genericStructure.getByText('show session statistic', { exact: false })).toBeVisible()
  await expect(genericStructure.getByText('空结果', { exact: true })).toBeVisible()
  for (const sectionIndex of [1, 2, 3, 4, 5]) {
    await genericStructure.getByText(new RegExp(`结构段 #${sectionIndex} ·`)).click()
  }
  await expect(genericStructure.getByText('Model', { exact: true })).toBeVisible()
  await expect(genericStructure.getByText('CR19000', { exact: true })).toBeVisible()
  await expect(genericStructure.getByText('Interface', { exact: true })).toBeVisible()
  await expect(genericStructure.getByText('user admin', { exact: true })).toBeVisible()
  await expect(genericStructure.getByText('interface GigabitEthernet1/0/1', { exact: true })).toBeVisible()
  await expect(genericStructure.getByText('diagnostic text', { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '原始 JSON' }).click()
  await expect(page.locator('.semantic-result__raw')).toContainText('"genericContent"')

  await page.getByRole('tab', { name: '请求快照' }).click()
  await expect(page.getByText('原提交快照（已脱敏）', { exact: true })).toBeVisible()
  const frozenSnapshot = page.locator('.request-snapshot')
  await expect(frozenSnapshot).toHaveCount(1)
  expect(JSON.parse((await frozenSnapshot.textContent())!).body.script.content).toBe(scriptContent)
  await expect(frozenSnapshot).toHaveCSS('background-color', 'rgb(16, 24, 32)')
  await expect(frozenSnapshot).toHaveCSS('color', 'rgb(244, 248, 249)')
  await expect(page.locator('.request-evidence')).not.toContainText('body.script.content')
  await expect(page.locator('.request-evidence')).toContainText('body.script.parserConfig')
  await expect(frozenSnapshot).toContainText('"schemaVersion": 1')
  await expect(frozenSnapshot).toContainText('/api/v1/projects/PROJ-ACCEPT/collections')
  await expect(frozenSnapshot).not.toContainText('registered-one-time-secret')
  const inputDownload = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载输入记录', exact: true }).click()
  const downloadedInput = await inputDownload
  expect(downloadedInput.suggestedFilename()).toMatch(/_input\.txt$/)
  expect(readFileSync((await downloadedInput.path())!).toString('utf8')).toBe(`\uFEFF${scriptContent}`)
  await expect(frozenSnapshot).toContainText('验收项目')
  await expect(frozenSnapshot).toContainText('核心交换机')
  await expect(frozenSnapshot).not.toContainText('上游已变更设备')

  const storageKeys = await page.evaluate(() => Object.keys(window.sessionStorage))
  expect(storageKeys).toHaveLength(1)
  expect(storageKeys[0]).toBe(oidcStorageKey)

  await page.getByRole('tab', { name: '快速连接' }).click()
  await formItem(page, 'IP / 主机名').locator('input').fill('10.20.30.41')
  await formItem(page, '用户名').locator('input').fill('direct-ops')
  await formItem(page, '主机密钥指纹（可选）')
    .locator('input')
    .fill('SHA256:direct-host-fingerprint')
  const directPassword = page.getByRole('textbox', { name: '密码', exact: true })
  await directPassword.fill('direct-one-time-secret')

  await page.getByRole('button', { name: '连接并执行采集' }).click()
  await expect.poll(() => safeSubmissions.length).toBe(2)
  const direct = safeSubmissions[1]
  const directBody = direct.body as GenericSubmissionBody
  expect(direct.credentialPresent).toBe(true)
  expect(directBody.namespace).toBe('standalone')
  expect(directBody.connection).toMatchObject({
    host: '10.20.30.41',
    username: 'direct-ops',
    hostKeyFingerprint: 'SHA256:direct-host-fingerprint'
  })
  expect(JSON.stringify(directBody)).not.toContain('direct-one-time-secret')
  await expect(page.getByText('采集 ID / collection-2')).toBeVisible()
  await expect(page.locator('.task-panel__state').getByText('采集成功', { exact: true })).toBeVisible({
    timeout: 10_000
  })
  await expect(page.getByRole('button', { name: '停止查看' })).toHaveCount(0)

  await page.getByRole('tab', { name: '请求快照' }).click()
  await expect(page.getByText('原提交快照（已脱敏）', { exact: true })).toBeVisible()
  await expect(page.locator('.request-snapshot').first()).toContainText('"path": "/api/v1/collections"')
  await expect(page.locator('.request-snapshot').first()).not.toContainText('direct-one-time-secret')
  const directDownload = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载输入记录', exact: true }).click()
  expect(readFileSync((await (await directDownload).path())!).toString('utf8')).toBe(`\uFEFF${scriptContent}`)

  const secondStorageKeys = await page.evaluate(() => Object.keys(window.sessionStorage))
  expect(secondStorageKeys).toHaveLength(1)
  expect(secondStorageKeys[0]).toBe(oidcStorageKey)

  expect(consoleProblems).toEqual([])
  expect(pageErrors).toEqual([])
  expect(failedRequests).toEqual([])

  const screenshot = resolve(import.meta.dirname, '../../docs/browser-acceptance.png')
  mkdirSync(resolve(screenshot, '..'), { recursive: true })
  await page.screenshot({ path: screenshot, fullPage: true })
})
