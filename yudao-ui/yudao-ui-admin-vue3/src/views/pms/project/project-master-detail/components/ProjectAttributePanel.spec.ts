import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const panel = readFileSync(new URL('./ProjectAttributePanel.vue', import.meta.url), 'utf8')
const detail = readFileSync(new URL('../index.vue', import.meta.url), 'utf8')
const api = readFileSync(
  new URL('../../../../../api/pms/project/projects/index.ts', import.meta.url),
  'utf8'
)

describe('F-PROJ-004 project attribute panel', () => {
  it('uses the controlled classify API with project version and idempotency', () => {
    expect(api).toContain('/actions/classify')
    expect(api).toMatch(/'Idempotency-Key': idempotencyKey/)
    expect(api).toMatch(/'If-Match': String\(expectedVersion\)/)
    expect(panel).toContain('createSubmissionIdempotencyState')
  })

  it('keeps CRM major level read-only and warns that frozen facts remain unchanged', () => {
    expect(panel).not.toMatch(/v-model="[^\"]*majorProjectLevel/)
    expect(panel).toContain('不适用')
    expect(panel).toContain('不会重新实例化')
    expect(panel).toContain('v-hasPermi="[\'pms:project:classify\']"')
    expect(panel.match(/:disabled="project\.sourceType !== 'MANUAL'"/g)).toHaveLength(2)
  })

  it('reuses Element Plus and supports theme-aware mobile layout', () => {
    expect(panel).toContain('<el-descriptions')
    expect(panel).toContain('<el-drawer')
    expect(panel).toContain('@media (width <= 767px)')
    expect(panel).toMatch(/var\(--el-text-color-/)
    expect(panel).not.toMatch(/#[0-9a-f]{3,8}\b/i)
    expect(panel).not.toMatch(/\sstyle=/)
  })

  it('is lazy-loaded inside the existing detail rail', () => {
    expect(detail).toContain("detail?.id && activeTab === 'attributes'")
    expect(detail).toContain('ProjectAttributePanel')
  })
})
