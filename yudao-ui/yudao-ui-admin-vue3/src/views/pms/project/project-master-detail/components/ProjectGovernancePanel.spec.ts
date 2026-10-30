import { existsSync, readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const exists = (path: string) => existsSync(new URL(path, import.meta.url))
const panel = read('./ProjectGovernancePanel.vue')
const detail = read('../index.vue')
const api = read('../../../../../api/pms/project/projects/index.ts')

describe('F-PROJ-006 project governance panel', () => {
  it('uses the locked guard, action and history APIs with concurrency headers', () => {
    expect(api).toContain("'ROLLBACK' | 'EXCEPTION_CLOSE' | 'REOPEN'")
    expect(api).toContain('/governance-guard')
    expect(api).toContain('/actions/rollback')
    expect(api).toContain('/actions/close')
    expect(api).toContain('/actions/reopen')
    expect(api).toContain('/governance-history')
    expect(api).toContain("'Idempotency-Key': idempotencyKey")
    expect(api).toContain("'If-Match': String(expectedVersion)")
  })

  it('queries the server guard before exposing the action form and submits its frozen version', () => {
    expect(panel).toMatch(/openAction[\s\S]*await loadGuard\(\)/)
    expect(panel).toContain('v-if="guard.allowed"')
    expect(panel).toContain('guard.value.projectVersion')
    expect(panel).toContain('guard.value.guardToken')
    expect(panel).toContain('guard.blockerTotal')
    expect(panel).toContain('<Pagination')
  })

  it('uses the stable permissions and state axes without showing normal-close reopen', () => {
    expect(panel).toContain('v-hasPermi="[\'pms:project:rollback\']"')
    expect(panel).toContain('v-hasPermi="[\'pms:project:close\']"')
    expect(panel).toContain('v-hasPermi="[\'pms:project:reopen\']"')
    expect(panel).toContain("project.lifecycleStatus === 'EXCEPTION_CLOSED'")
    expect(panel).not.toMatch(/NORMAL_CLOSED[^\n]*openAction\('REOPEN'\)/)
    expect(detail).toContain('v-hasPermi="[\'pms:project:governance:query\']"')
  })

  it('captures required reasons, business basis, reassignment and structured legacy items', () => {
    expect(panel).toContain('PMS_PROJECT_GOVERNANCE_REASON')
    expect(panel).toContain('reassignmentRequirement')
    expect(panel).toContain('businessBasis')
    expect(panel).toContain('legacyItems')
    expect(panel).toContain('exceptionCloseSnapshotId')
  })

  it('reuses Element Plus with responsive theme-token layouts and no inline styles', () => {
    expect(panel).toContain('<el-descriptions')
    expect(panel).toContain('<el-table')
    expect(panel).toContain('<Dialog')
    expect(panel).toContain('<el-form')
    expect(panel).toContain('@media (width <= 1199px)')
    expect(panel).toContain('@media (width <= 767px)')
    expect(panel).toMatch(/var\(--el-(?:text|border)-/)
    expect(panel).not.toMatch(/\sstyle=/)
    expect(panel).not.toMatch(/#[0-9a-f]{3,8}\b/i)
  })

  it('removes the V1.7 page and API while retaining the current governance panel', () => {
    expect(exists('../../project-governance/index.vue')).toBe(false)
    expect(exists('../../../../../api/pms/project/project-governance/index.ts')).toBe(false)
  })
})
