import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import { getMemberCandidates, saveMember, removeMember } from '@/api/pms/project/unified-members'
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
describe('unified member entry contract', () => {
  beforeEach(() => vi.clearAllMocks())
  it('carries current project, concurrency version and stable caller key', async () => {
    const data = { userId: 7, memberRole: 'TEAM_MEMBER' as const, reason: '加入' }
    await saveMember(9, undefined, data, 2, 'intent')
    expect(request.post).toHaveBeenCalledWith({ url: '/api/v1/pms/projects/9/members', data, headers: { 'If-Match': '2', 'Idempotency-Key': 'intent' } })
    await saveMember(9, 12, data, 3, 'edit-intent')
    expect(request.put).toHaveBeenCalledWith(expect.objectContaining({ url: '/api/v1/pms/projects/9/members/12' }))
    await removeMember(9, 12, '交接', 4, 'remove-intent')
    expect(request.post).toHaveBeenLastCalledWith(expect.objectContaining({ url: '/api/v1/pms/projects/9/members/12/actions/remove', data: { reason: '交接', replacementPrimaryUserId: undefined } }))
    await getMemberCandidates(9, { pageNo: 1, pageSize: 20, keyword: '张', projectRole: 'TEAM_MEMBER' })
    expect(request.get).toHaveBeenCalledWith({ url: '/api/v1/pms/projects/9/member-candidates', params: { pageNo: 1, pageSize: 20, keyword: '张', projectRole: 'TEAM_MEMBER' } })
  })
  it('new entry uses one relation panel and does not write the old team API', () => {
    const detail = readFileSync(resolve('src/views/pms/project/inheritance/detail/index.vue'), 'utf8')
    const list = readFileSync(resolve('src/views/pms/project/inheritance/projects/index.vue'), 'utf8')
    const panel = readFileSync(resolve('src/views/pms/project/inheritance/members/ProjectMembersPanel.vue'), 'utf8')
    expect(detail).toContain('<ProjectMembersPanel')
    expect(detail).not.toContain("activeTab === 'service-managers'")
    expect(list).toContain("section: 'members'")
    expect(list).not.toContain('assignVisible')
    expect(panel).not.toContain('/api/pms/project/project-team')
    expect(panel).not.toMatch(/label="需求人数"|requiredCount|headCount/)
    expect(panel).toContain('<UnifiedMemberForm')
    expect(panel).not.toContain('<ProjectMemberForm')
    expect(panel).not.toContain('<ServiceManagerForm')
  })
})
