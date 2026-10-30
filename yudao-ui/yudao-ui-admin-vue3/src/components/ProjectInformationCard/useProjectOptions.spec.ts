import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { mount } from '@/views/pms/cutover/cutoverTestHarness'
import { searchProjectOptions } from '@/api/pms/project/selection'
import { useProjectOptions } from './useProjectOptions'
vi.mock('@/api/pms/project/selection', () => ({ searchProjectOptions: vi.fn() }))
beforeEach(() => vi.resetAllMocks())
const option = (id: string) => ({ id, code: `P-${id}`, name: `项目${id}` })

it('retains the selected option during remote search and discards an earlier response', async () => {
  let state: ReturnType<typeof useProjectOptions>
  const view = mount(defineComponent({ setup() { state = useProjectOptions(() => '101'); return () => h('div') } }))
  vi.mocked(searchProjectOptions).mockResolvedValueOnce([option('101')])
  await state!.search('101')
  let resolveOld: (value: any) => void = () => {}
  vi.mocked(searchProjectOptions).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
  const old = state!.search('old')
  vi.mocked(searchProjectOptions).mockResolvedValueOnce([option('102')])
  await state!.search(' new ')
  resolveOld([option('103')]); await old; await nextTick()
  expect(state!.options.value.map(row => row.id)).toEqual(['101', '102'])
  expect(searchProjectOptions).toHaveBeenLastCalledWith('new')
  view.app.unmount()
})

it('reset invalidates pending requests so a closed create page does not regain candidates', async () => {
  let state: ReturnType<typeof useProjectOptions>
  const view = mount(defineComponent({ setup() { state = useProjectOptions(() => ''); return () => h('div') } }))
  let resolvePending: (value: any) => void = () => {}
  vi.mocked(searchProjectOptions).mockImplementationOnce(() => new Promise(resolve => { resolvePending = resolve }))
  const pending = state!.search()
  state!.reset(); resolvePending([option('101')]); await pending
  expect(state!.options.value).toEqual([])
  expect(state!.loading.value).toBe(false)
  view.app.unmount()
})
