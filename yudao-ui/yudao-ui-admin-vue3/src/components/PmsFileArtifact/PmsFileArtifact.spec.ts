import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const api = read('../../api/pms/platform/file/index.ts')
const uploader = read('./PmsFileUploader.vue')
const references = read('./PmsFileReferenceList.vue')
const versions = read('./PmsFileVersionDrawer.vue')
const durationForm = read(
  '../../views/pms/project/project-master-detail/components/ProjectDurationFormDrawer.vue'
)
const durationPanel = read(
  '../../views/pms/project/project-master-detail/components/ProjectDurationPanel.vue'
)
const durationApi = read('../../api/pms/engineering/construction-plan/index.ts')

describe('F-PLT-001 shared file components', () => {
  it('uses stable PLT routes and never accepts tenant or permanent URLs as identity', () => {
    expect(api).toContain("const baseUrl = '/api/v1/pms'")
    expect(api).toContain('/files:init-upload')
    expect(api).toContain(':complete-upload')
    expect(api).toContain('/access-tickets')
    expect(api).toContain("'Idempotency-Key': idempotencyKey")
    expect(api).toContain("'If-Match': String(referenceVersion)")
    expect(api).not.toMatch(/tenantId|storagePath|configId|permanentUrl/)
  })

  it('separates transfer progress from server validation and only emits committed versions', () => {
    expect(uploader).toContain("stage.value = 'UPLOADING'")
    expect(uploader).toContain("stage.value = 'VALIDATING'")
    expect(uploader).toContain('FileApi.initializeUpload')
    expect(uploader).toContain('FileApi.completeUpload')
    expect(uploader).toContain('attempt.value.initKey')
    expect(uploader).toContain('attempt.value.completeKey')
    expect(uploader).toContain("emit('completed', completed)")
  })

  it('uses server allowed actions for access and short-lived tickets', () => {
    expect(references).toContain("artifact.allowedActions.includes('PREVIEW')")
    expect(references).toContain("artifact.allowedActions.includes('DOWNLOAD')")
    expect(references).toContain('FileApi.createAccessTicket')
    expect(references).toContain("window.open('about:blank', '_blank')")
    expect(references).toContain('ticket.shortLivedUrl')
    expect(references).not.toMatch(/infraFileId|storagePath/)
  })

  it('provides stable cursor history and responsive narrow layouts with theme tokens', () => {
    expect(versions).toContain('nextCursor')
    expect(versions).toContain('hasMore')
    for (const component of [uploader, references, versions]) {
      expect(component).not.toMatch(/\sstyle=/)
      expect(component).not.toMatch(/#[0-9a-f]{3,8}\b/i)
      expect(component).toMatch(/var\(--el-/)
    }
    expect(references).toContain('@media (width <= 767px)')
    expect(versions).toContain("narrow.value ? '100%' : '720px'")
  })

  it('freezes the complete customer-delay evidence tuple and hides the slot for other reasons', () => {
    expect(durationForm).toContain("form.reasonType === 'CUSTOMER_DELAY'")
    expect(durationForm).toContain('customerEvidenceFileId: selection.artifactId')
    expect(durationForm).toContain('customerEvidenceFileVersion: selection.versionNo')
    expect(durationForm).toContain('customerEvidenceReferenceKey: selection.referenceKey')
    expect(durationPanel).toContain(
      'draft.value.customerEvidenceRequired && !draft.value.customerEvidenceFileId'
    )
    expect(durationPanel).toContain('<PmsFileReferenceList')
    expect(durationApi).toContain('customerEvidenceReferenceKey?: string')
  })
})
