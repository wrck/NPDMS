import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const panel = read('./ProjectServiceManagerPanel.vue')
const detail = read('../index.vue')
const projectList = read('../../projects/index.vue')
const api = read('../../../../../api/pms/project/projects/index.ts')

describe('F-PROJ-005 service manager assignment UI', () => {
  it('uses exact candidates and refreshes them before assignment submission', () => {
    expect(projectList).toContain('getServiceManagerCandidates')
    expect(projectList).toContain('assignCandidates')
    expect(projectList).toContain('下单办事处：')
    expect(projectList).toMatch(
      /const refreshedCandidates = await loadAssignCandidates[\s\S]*ProjectsApi\.assignManager/
    )
    expect(projectList).not.toMatch(/v-model="assignForm\.effectiveFrom"/)
    expect(api).toContain('/service-manager-candidates')
    expect(api).toContain('/actions/assign-manager')
  })

  it('shows primary and collaborator responsibility by project node with bounded paging', () => {
    expect(panel).toContain('pageSize: 10')
    expect(panel).toContain('scope.primaryManager?.memberName')
    expect(panel).toContain('scope.collaborators.map')
    expect(panel).toContain('scope.departmentCode')
    expect(panel).toContain('scope.siteId')
    expect(panel).toContain('row.assignmentStatus')
    expect(panel).toContain('<Pagination')
    expect(api).toContain('/service-manager-responsibilities')
  })

  it('is permission-controlled and lazy-loaded from project detail', () => {
    expect(detail).toContain("visitedTabs.has('service-managers')")
    expect(detail).toContain('ProjectServiceManagerPanel')
    expect(detail).toContain('v-hasPermi="[\'pms:project:assign\']"')
  })

  it('uses responsive Element Plus layouts and theme tokens', () => {
    expect(panel).toContain('class="table-scroll desktop-list"')
    expect(panel).toContain('class="mobile-list"')
    expect(panel).toContain('@media (width <= 1199px)')
    expect(panel).toContain('@media (width <= 767px)')
    expect(panel).toMatch(/var\(--el-(?:text|border)-/)
    expect(panel).not.toMatch(/#[0-9a-f]{3,8}\b/i)
    expect(panel).not.toMatch(/\sstyle=/)
  })
})
