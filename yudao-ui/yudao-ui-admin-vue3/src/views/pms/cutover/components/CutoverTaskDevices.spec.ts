import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getCurrentDeviceSummaries, getDevice } from '@/api/pms/asset/device'
import { checkPermi } from '@/utils/permission'
import { mount, textOf } from '../cutoverTestHarness'
import Panel from './CutoverTaskDevices.vue'

const firstId = '9007199254740995'
const secondId = '9007199254740996'
const projectId = '9007199254741999'
const detail = () => ({ task: { projectId }, devices: [{ deviceId: firstId, serialNumber: 'SN-A' }, { deviceId: secondId, serialNumber: 'SN-B' }] })
const flush = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }

describe('P2 current device summaries', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(checkPermi).mockReturnValue(true) })

  it('uses one batch and maps unordered string IDs without restricting current project', async () => {
    vi.mocked(getCurrentDeviceSummaries).mockResolvedValue([
      { deviceId: secondId, sn: 'SN-B', productModel: '型号B', projectId: 'other' },
      { deviceId: firstId, sn: 'SN-A', productModel: '型号A' }
    ] as any)
    const page = mount(Panel, { detail: detail() })
    await flush()
    expect(getCurrentDeviceSummaries).toHaveBeenCalledExactlyOnceWith([firstId, secondId], projectId)
    expect(getDevice).not.toHaveBeenCalled()
    expect((page.vm as any).$.setupState.rows.map((row: any) => row.productModel)).toEqual(['型号A', '型号B'])
    page.app.unmount()
  })

  it.each(['missing', 'wrong SN', 'failure'])('keeps partial failure visible for %s', async condition => {
    if (condition === 'failure') vi.mocked(getCurrentDeviceSummaries).mockRejectedValue(new Error('offline'))
    else vi.mocked(getCurrentDeviceSummaries).mockResolvedValue([
      { deviceId: secondId, sn: condition === 'wrong SN' ? 'FOREIGN' : 'SN-B', productModel: '型号B' }
    ] as any)
    const page = mount(Panel, { detail: detail() }); await flush()
    expect(textOf(page.root)).toContain('部分产品信息暂时无法读取')
    const rows = (page.vm as any).$.setupState.rows
    expect(rows[0].productModel).toBe('—')
    expect(rows[1].productModel).toBe(condition === 'missing' ? '型号B' : '—')
    page.app.unmount()
  })

  it('does not request summaries without asset permission or for empty scope', async () => {
    vi.mocked(checkPermi).mockReturnValue(false)
    const denied = mount(Panel, { detail: detail() }); await flush(); denied.app.unmount()
    vi.mocked(checkPermi).mockReturnValue(true)
    const empty = mount(Panel, { detail: { devices: [] } }); await flush(); empty.app.unmount()
    expect(getCurrentDeviceSummaries).not.toHaveBeenCalled()
  })

  it('ignores a late batch after task scope changes', async () => {
    let resolveOld!: (value: any) => void
    vi.mocked(getCurrentDeviceSummaries).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValueOnce([{ deviceId: secondId, sn: 'SN-B', productModel: '当前型号' }] as any)
    const current = ref(detail())
    const page = mount(defineComponent({ setup: () => () => h(Panel, { detail: current.value as any }) }))
    await flush()
    current.value = { task: { projectId }, devices: [detail().devices[1]] }; await flush()
    resolveOld([{ deviceId: firstId, sn: 'SN-A', productModel: '过期型号' }]); await flush()
    expect(textOf(page.root)).toContain('当前型号')
    expect(textOf(page.root)).not.toContain('过期型号')
    page.app.unmount()
  })
})
