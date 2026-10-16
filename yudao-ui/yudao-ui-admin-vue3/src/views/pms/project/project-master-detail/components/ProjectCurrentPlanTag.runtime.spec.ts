import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import ProjectCurrentPlanTag from './ProjectCurrentPlanTag.vue'
import { getProjectPlan, type ProjectPlanState } from '@/api/pms/project/projects/projectPlan'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/projects/projectPlan', () => ({ getProjectPlan: vi.fn() }))
vi.mock('@/utils/dict', () => ({
  DICT_TYPE: { PMS_PROJECT_CATEGORY: 'category', PMS_SIGNING_METHOD: 'signing', PMS_IMPLEMENTATION_METHOD: 'method', PMS_MAJOR_PROJECT_LEVEL: 'level' },
  getDictLabel: (_dict: string, value: string) => (({ ENGINEERING: '工程类', DIRECT_SIGN: '直签' } as Record<string, string>)[value] || value)
}))
const settle = async () => { await nextTick(); await nextTick(); await nextTick() }
const state = (revisionNo: number) => ({
  effective: { revisionNo, designer: { match: { projectCategory: 'ENGINEERING', signingMethod: 'DIRECT_SIGN' } } },
  draft: { revisionNo: 99, designer: { match: { signingMethod: 'UNPUBLISHED' } } }
}) as unknown as ProjectPlanState
beforeEach(() => vi.clearAllMocks())

it('shows the effective configuration and plan revision, never the unpublished draft', async () => {
  vi.mocked(getProjectPlan).mockResolvedValue(state(4))
  const { app, root } = mount(ProjectCurrentPlanTag, { projectId: 197 })
  await settle()
  expect(textOf(root)).toContain('当前执行：工程类－直签 · 计划 v4')
  expect(textOf(root)).not.toContain('99')
  expect(textOf(root)).not.toContain('UNPUBLISHED')
  app.unmount()
})

it('clears stale plan information when a refreshed read fails', async () => {
  const version = ref(7)
  vi.mocked(getProjectPlan).mockResolvedValueOnce(state(4)).mockRejectedValueOnce(new Error('denied'))
  const wrapper = defineComponent({ setup: () => () => h(ProjectCurrentPlanTag, { projectId: 197, refreshToken: version.value }) })
  const { app, root } = mount(wrapper)
  await settle()
  version.value = 8
  await settle()
  expect(textOf(root)).toContain('当前执行计划读取失败')
  expect(textOf(root)).not.toContain('计划 v4')
  app.unmount()
})

it('does not let a previous project response replace the current project plan', async () => {
  const id = ref(197)
  let resolvePrevious!: (value: ProjectPlanState) => void
  vi.mocked(getProjectPlan).mockReturnValueOnce(new Promise(resolve => { resolvePrevious = resolve }))
    .mockResolvedValueOnce(state(2))
  const wrapper = defineComponent({ setup: () => () => h(ProjectCurrentPlanTag, { projectId: id.value }) })
  const { app, root } = mount(wrapper)
  id.value = 66
  await settle()
  resolvePrevious(state(4))
  await settle()
  expect(textOf(root)).toContain('计划 v2')
  expect(textOf(root)).not.toContain('计划 v4')
  app.unmount()
})
