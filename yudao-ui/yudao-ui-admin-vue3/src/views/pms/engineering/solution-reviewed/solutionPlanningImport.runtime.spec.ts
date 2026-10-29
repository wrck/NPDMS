import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import PlanningImport from './SolutionPlanningImport.vue'
import { planningTemplate } from './solutionPlanningImport'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/download', () => ({ default: { file: vi.fn() } }))

const render = () => {
  const readOnly = ref(false)
  const imported = vi.fn()
  let state: any
  const wrapper = defineComponent({ setup: () => () => h(PlanningImport, {
    section: 'interface', readOnly: readOnly.value, onImported: imported,
    ref: value => { if (value) state = (value as any).$.setupState }
  }) })
  const mounted = mount(wrapper)
  const select = (text: () => Promise<string>) => { state.input = { files: [{ name: '接口.csv', text }], value: 'selected' } }
  return { ...mounted, imported, readOnly, select, get state() { return state } }
}

describe('planning CSV import interaction', () => {
  it('emits the complete parsed batch and clears the selection for a repeat import', async () => {
    const mounted = render()
    try {
      mounted.select(async () => planningTemplate('interface') + '001,GE1,GE2,光口,模块,说明\r\n')
      await mounted.state.importFile()
      expect(mounted.imported).toHaveBeenCalledOnce()
      expect(mounted.imported.mock.calls[0][0]).toEqual([{ localDevice: '001', localPort: 'GE1', peerPort: 'GE2', portType: '光口', cableModel: '模块', remark: '说明' }])
      expect(mounted.state.input.value).toBe('')
    } finally { mounted.app.unmount() }
  })
  it('rejects a malformed later row without importing the preceding valid row', async () => {
    const mounted = render()
    try {
      mounted.select(async () => planningTemplate('interface') + '001,GE1,GE2,光口,模块,说明\r\nwrong,row\r\n')
      await mounted.state.importFile()
      expect(mounted.imported).not.toHaveBeenCalled()
      expect(mounted.state.error).toContain('第3行')
      expect(mounted.state.loading).toBe(false)
    } finally { mounted.app.unmount() }
  })
  it('does not import after the form becomes read-only while the file is being read', async () => {
    const mounted = render()
    let finish!: (value: string) => void
    try {
      mounted.select(() => new Promise(resolve => { finish = resolve }))
      const pending = mounted.state.importFile()
      mounted.readOnly.value = true
      await nextTick()
      finish(planningTemplate('interface') + '001,GE1,GE2,光口,模块,说明\r\n')
      await pending
      expect(mounted.imported).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })
})
