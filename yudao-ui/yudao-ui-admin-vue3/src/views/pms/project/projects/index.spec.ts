import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
// @ts-expect-error Node的TypeScript测试运行器需要显式扩展名，生产代码仍使用标准无扩展名导入。
import { createSubmissionIdempotencyState } from './submissionIdempotency.ts'

describe('F-PROJ-001 creation submission state', () => {
  it('reuses a key only while the request remains unchanged', () => {
    let sequence = 0
    const state = createSubmissionIdempotencyState(() => `key-${++sequence}`)
    const original = { projectName: '项目A', templateRevisionId: 9001 }

    assert.equal(state.keyFor(original), 'key-1')
    assert.equal(state.keyFor({ ...original }), 'key-1')
    assert.equal(state.keyFor({ ...original, projectName: '项目A-修正' }), 'key-2')
  })

  it('starts a new operation after the wizard is reset', () => {
    let sequence = 0
    const state = createSubmissionIdempotencyState(() => `key-${++sequence}`)

    assert.equal(state.keyFor({ projectName: '项目A' }), 'key-1')
    state.reset()
    assert.equal(state.keyFor({ projectName: '项目A' }), 'key-2')
  })

  it('keeps the creation form in memory only', () => {
    const source = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')

    assert.doesNotMatch(source, /localStorage|sessionStorage|indexedDB/i)
  })

  it('submits the selected revision and candidate watermark', () => {
    const source = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')

    assert.match(source, /templateRevisionId: selectedTemplateRevisionId\.value/)
    assert.match(source, /candidateWatermark: matchResult\.value\?\.candidateWatermark/)
    assert.doesNotMatch(source, /templateId: selectedTemplateId\.value/)
  })

  it('sends required idempotency and project version headers for assignment', () => {
    const source = readFileSync(
      new URL('../../../../api/pms/project/projects/index.ts', import.meta.url),
      'utf8'
    )

    assert.match(source, /'Idempotency-Key': idempotencyKey/)
    assert.match(source, /'If-Match': String\(expectedVersion\)/)
  })

  it('uses organization selectors and a multi-site implementation scope', () => {
    const source = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')

    assert.doesNotMatch(source, /officeId|locationId/)
    assert.match(source, /orderOfficeCompanyId/)
    assert.match(source, /orderOfficeDepartmentId/)
    assert.match(source, /createForm\.sites/)
    assert.match(source, /primarySiteIndex/)
    assert.match(source, /UNRESOLVED/)
    assert.match(source, /resolveAreaDepartment/)
    assert.match(source, /departmentCode/)
  })

  it('keeps survey and installation location maintenance atomic and equipment location read-only', () => {
    const surveySource = readFileSync(
      new URL('../../engineering/site-survey/index.vue', import.meta.url),
      'utf8'
    )
    const installationSource = readFileSync(
      new URL('../../engineering/installation/index.vue', import.meta.url),
      'utf8'
    )
    const equipmentSource = readFileSync(
      new URL('../../asset/equipment/index.vue', import.meta.url),
      'utf8'
    )

    assert.match(surveySource, /locationMaintenance/)
    assert.match(installationSource, /locationMaintenance/)
    assert.match(installationSource, /getEquipmentVersionList/)
    for (const source of [surveySource, installationSource, equipmentSource]) {
      assert.match(source, /@\/api\/pms\/project\/projects/)
      assert.match(source, /label-field="projectName"/)
      assert.match(source, /query-field="projectName"/)
      assert.doesNotMatch(source, /@\/api\/pms\/project\/project'/)
    }
    assert.doesNotMatch(equipmentSource, /v-model="form\.location"/)
    assert.match(equipmentSource, /位置变更历史/)
  })
})
