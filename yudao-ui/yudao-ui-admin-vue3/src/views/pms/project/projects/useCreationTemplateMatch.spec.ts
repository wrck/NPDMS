import { defineComponent, h, reactive } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { mount } from '../../platform/dynamic-form/components/runtimeTestHarness'
import { useCreationTemplateMatch } from './useCreationTemplateMatch'
import { matchTemplates, type ProjectMatchTemplatesRespVO } from '@/api/pms/project/projects'

vi.mock('@/api/pms/project/projects', () => ({ matchTemplates: vi.fn() }))
const response = { outcome: 'MATCHED', candidateWatermark: 'current', candidates: [], conflicts: [] } as ProjectMatchTemplatesRespVO
const setup = () => {
  const input = reactive({ projectName: '现场工勘', signingMethod: 'DIRECT_SIGN', projectCategory: 'GENERAL',
    implementationMode: 'DIRECT_SERVICE', orderOfficeCompanyId: 1, orderOfficeDepartmentId: 2 })
  let state!: ReturnType<typeof useCreationTemplateMatch>
  const view = mount(defineComponent({ setup() { state = useCreationTemplateMatch(() => ({ ...input })); return () => h('div') } }))
  return { ...view, input, state }
}
beforeEach(() => vi.clearAllMocks())
it('sends all creation inputs and invalidates a previous choice immediately on changes', async () => {
  vi.mocked(matchTemplates).mockResolvedValue(response)
  const view = setup()
  await view.state.runMatch()
  expect(matchTemplates).toHaveBeenCalledWith({ ...view.input })
  view.state.selectedTemplateRevisionId.value = 22
  view.input.projectName = '需求分析'
  expect(view.state.matchResult.value).toBeNull()
  expect(view.state.selectedTemplateRevisionId.value).toBeUndefined()
  view.app.unmount()
})
it('late responses cannot restore old results or clear a newer loading state', async () => {
  const resolve: Array<(result: ProjectMatchTemplatesRespVO) => void> = []
  vi.mocked(matchTemplates).mockImplementation(() => new Promise(done => resolve.push(done)))
  const view = setup()
  const old = view.state.runMatch()
  view.input.orderOfficeDepartmentId = 3
  const current = view.state.runMatch()
  resolve[0](response); await old
  expect(view.state.matchResult.value).toBeNull()
  expect(view.state.matchLoading.value).toBe(true)
  resolve[1]({ ...response, candidateWatermark: 'new' }); await current
  expect(view.state.matchResult.value?.candidateWatermark).toBe('new')
  view.app.unmount()
})
it('failed retry clears previous result and unmount ignores pending work', async () => {
  vi.mocked(matchTemplates).mockResolvedValueOnce(response).mockRejectedValueOnce(new Error('offline'))
  const view = setup()
  await view.state.runMatch()
  await expect(view.state.runMatch()).rejects.toThrow('offline')
  expect(view.state.matchResult.value).toBeNull()
  expect(view.state.matchLoading.value).toBe(false)
  let resolve!: (result: ProjectMatchTemplatesRespVO) => void
  vi.mocked(matchTemplates).mockImplementation(() => new Promise(done => { resolve = done }))
  const pending = view.state.runMatch()
  view.app.unmount(); resolve(response); await pending
  expect(view.state.matchResult.value).toBeNull()
})
