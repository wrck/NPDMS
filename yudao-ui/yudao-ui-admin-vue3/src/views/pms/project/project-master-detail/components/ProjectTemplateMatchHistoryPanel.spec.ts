import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const panel = read('./ProjectTemplateMatchHistoryPanel.vue')
const detail = read('../index.vue')
const api = read('../../../../../api/pms/project/projects/index.ts')

describe('F-PROJ-004 template match history panel', () => {
  it('queries project-scoped history with bounded paging and stable sorting', () => {
    expect(api).toContain('/template-match-history')
    expect(panel).toContain('pageSize: 10')
    expect(panel).toContain("orderBy: 'occurredAt'")
    expect(panel).toContain('ascending: false')
    expect(panel).toContain('<Pagination')
  })

  it('shows matching evidence and audit associations', () => {
    expect(panel).toContain('beforeAttributeSnapshot')
    expect(panel).toContain('attributeSnapshot')
    expect(panel).toContain('candidateDigest')
    expect(panel).toContain('operationId')
    expect(panel).toContain('auditLogId')
    expect(panel).toContain('查看完整匹配证据')
    expect(panel).toContain("row.beforeAttributeSnapshot || '首次创建，无前值'")
    expect(panel).toContain('操作者：{{ row.operatorId }}')
  })

  it('switches between desktop table and mobile cards using theme tokens', () => {
    expect(panel).toContain('class="table-scroll desktop-list"')
    expect(panel).toContain('class="mobile-list"')
    expect(panel).toContain('@media (width <= 1199px)')
    expect(panel).toContain('@media (width <= 767px)')
    expect(panel).toMatch(/var\(--el-(?:text|border|fill)-/)
    expect(panel).not.toMatch(/#[0-9a-f]{3,8}\b/i)
    expect(panel).not.toMatch(/\sstyle=/)
  })

  it('is lazy-loaded without adding a standalone route', () => {
    expect(detail).toContain("detail?.id && activeTab === 'match-history'")
    expect(detail).toContain('ProjectTemplateMatchHistoryPanel')
  })
})
