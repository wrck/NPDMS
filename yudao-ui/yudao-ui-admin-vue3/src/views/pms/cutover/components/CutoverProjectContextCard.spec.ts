import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Card from './CutoverProjectContextCard.vue'
import { mount, textOf } from '../cutoverTestHarness'
import * as projects from '@/api/pms/project/projects'

vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn(), getProjectMembers: vi.fn() }))
const detail = { task: { projectId: '30', projectName: '原任务项目', scheduledTime: null }, source: { intakeSourceType: 'SELF_CREATED' },
  project: { projectName: '原任务项目', departmentName: '原办事处' }, assessment: null, customerServiceLevel: null } as any
const flush = async () => { for (let i = 0; i < 8; i++) await nextTick() }
beforeEach(() => vi.clearAllMocks())
describe('审批只读任务项目来源', () => {
  it('uses captured CUT project facts without querying a project outside the viewer scope', async () => {
    const page = mount(Card, { detail, approvalReadonly: true })
    await flush()
    expect(textOf(page.root)).toContain('原任务项目')
    expect(textOf(page.root)).toContain('原办事处')
    expect(projects.getProject).not.toHaveBeenCalled()
    expect(projects.getProjectMembers).not.toHaveBeenCalled()
    page.app.unmount()
  })
})
