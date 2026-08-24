import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const panel = readFileSync(new URL('./ProjectAuthorizationPanel.vue', import.meta.url), 'utf8')
const detail = readFileSync(new URL('../index.vue', import.meta.url), 'utf8')
const api = readFileSync(
  new URL('../../../../../api/pms/project/projects/index.ts', import.meta.url),
  'utf8'
)

describe('F-PROJ-003 project authorization panel', () => {
  it('uses strict action and scope contracts with required write headers', () => {
    expect(api).toMatch(/'PROJECT_VIEW' \| 'PROJECT_MANAGE'/)
    expect(api).toMatch(/'CURRENT_PROJECT' \| 'PROJECT_AND_DESCENDANTS'/)
    expect(api).toMatch(/authorization-grants/)
    expect(api).toMatch(/'Idempotency-Key': idempotencyKey/)
    expect(api).toMatch(/'If-Match': String\(expectedVersion\)/)
  })

  it('reuses Yudao and Element Plus components under permission controls', () => {
    expect(panel).toContain('UserSelect')
    expect(panel).toContain('UserApi.getSimpleUserList()')
    expect(panel).not.toContain('UserSelectV2')
    expect(panel).not.toContain('UserApi.getUserList(')
    expect(panel).toContain('<dict-tag')
    expect(panel).toContain('<Pagination')
    expect(panel).toContain('v-hasPermi="[\'pms:project:authorization:manage\']"')
    expect(panel).toContain('v-hasPermi="[\'pms:project:authorization:revoke\']"')
    expect(panel).not.toMatch(/\sstyle=/)
  })

  it('keeps form state until create or revoke succeeds', () => {
    expect(panel).toMatch(
      /await ProjectsApi\.createProjectAuthorization[\s\S]*createVisible\.value = false/
    )
    expect(panel).toMatch(
      /await ProjectsApi\.revokeProjectAuthorization[\s\S]*revokeVisible\.value = false/
    )
    expect(panel).not.toMatch(/catch\s*\([^)]*\)\s*\{[^}]*Visible\.value = false/)
  })

  it('provides desktop, tablet and mobile layouts using theme tokens', () => {
    expect(panel).toContain('class="table-scroll desktop-list"')
    expect(panel).toContain('class="mobile-list"')
    expect(panel).toContain('@media (width <= 1199px)')
    expect(panel).toContain('@media (width <= 767px)')
    expect(panel).toMatch(/var\(--el-(?:text|border)-/)
    expect(panel).not.toMatch(/#[0-9a-f]{3,8}\b/i)
  })

  it('is lazy-loaded from the existing project detail rail', () => {
    expect(detail).toContain("visitedTabs.has('authorization')")
    expect(detail).toContain('ProjectAuthorizationPanel')
    expect(detail).toContain('v-hasPermi="[\'pms:project:authorization:query\']"')
    expect(detail).not.toMatch(/path:\s*['\"][^'\"]*authorization/)
  })
})
