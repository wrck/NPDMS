import { defineComponent, h, nextTick, ref, watch } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { mount } from '@/views/pms/cutover/cutoverTestHarness'
import { getProjectContext } from '@/api/pms/project/selection'
import { useProjectInformation } from './useProjectInformation'

vi.mock('@/api/pms/project/selection', () => ({ getProjectContext: vi.fn() }))
const flush = async () => { for (let i = 0; i < 8; i++) await nextTick() }
beforeEach(() => { vi.resetAllMocks() })
const context = (id: string) => ({ project: { projectId: id, projectCode: `P-${id}`, projectName: `项目${id}` }, customerServiceLevel: '一级' })

it('discards late project responses and clears when selection is removed', async () => {
  const id = ref('101')
  let resolveOld: (value: any) => void = () => {}
  vi.mocked(getProjectContext).mockImplementation(async value => String(value) === '101'
    ? new Promise(resolve => { resolveOld = resolve }) : context('102'))
  let state: ReturnType<typeof useProjectInformation>
  const view = mount(defineComponent({ setup() {
    state = useProjectInformation()
    watch(id, value => { void state.load(value) }, { immediate: true })
    return () => h('div', state.project.value?.projectCode)
  } }))
  id.value = '102'; await flush()
  expect(state!.project.value?.projectCode).toBe('P-102')
  expect(state!.customerServiceLevel.value).toBe('一级')
  resolveOld(context('101')); await flush()
  expect(state!.project.value?.projectCode).toBe('P-102')
  id.value = ''; await flush()
  expect(state!.project.value).toBeNull()
  expect(getProjectContext).toHaveBeenCalledTimes(2)
  view.app.unmount()
})

it('never keeps old project facts after the next project request fails', async () => {
  vi.mocked(getProjectContext).mockResolvedValueOnce(context('101')).mockRejectedValueOnce(new Error('forbidden'))
  let state: ReturnType<typeof useProjectInformation>
  const view = mount(defineComponent({ setup() { state = useProjectInformation(); return () => h('div') } }))
  await state!.load('101')
  await state!.load('102')
  expect(state!.project.value).toBeNull()
  expect(state!.error.value).toContain('加载失败')
  expect(state!.loading.value).toBe(false)
  view.app.unmount()
})
