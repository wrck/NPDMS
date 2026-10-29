import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import ProjectDeviceSelect from './index.vue'
import { getDeviceArchivePage } from '@/api/pms/asset/device/archive'
import { findByTestId, mount, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/asset/device/archive', () => ({ getDeviceArchivePage: vi.fn() }))
const select = defineComponent({ setup(_, { attrs, slots }) {
  return () => h('select', attrs, [slots.default?.(), slots.footer?.()])
} })
const option = defineComponent({ setup(_, { attrs }) { return () => h('option', attrs) } })
const flush = async () => { for (let i = 0; i < 12; i++) await nextTick() }
const nodes = (node: TestNode, type: string): TestNode[] => [
  ...(node.type === type ? [node] : []), ...node.children.flatMap(child => nodes(child, type))
]
const devices = (start: number, count: number) => Array.from({ length: count }, (_, index) => ({
  id: String(BigInt('2115000000000000000') + BigInt(start + index)), sn: `SN${start + index}`, name: '项目设备'
}))
const render = (props: Record<string, unknown> = {}) => {
  const changed = vi.fn()
  const mounted = mount(ProjectDeviceSelect, { projectId: '84459', 'onUpdate:modelValue': changed, ...props }, { ElSelect: select, ElOption: option })
  return { ...mounted, changed, state: (mounted.vm as any).$.setupState }
}
const click = async (root: TestNode, id: string) => {
  const target = findByTestId(root, id)
  expect(target, `missing ${id}`).toBeDefined()
  await (target!.props!.onClick as () => Promise<void>)()
  await flush()
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getDeviceArchivePage).mockImplementation(async query => {
    if (query.pageNo == null) throw new Error('候选设备查询缺少页码')
    return { list: devices((query.pageNo - 1) * 50 + 1, query.pageNo === 3 ? 30 : 50), total: 130 }
  })
})

it('loads all candidate pages and selects a device after the first 50 without losing ID precision', async () => {
  const m = render()
  try {
    await flush()
    expect(nodes(m.root, 'option')).toHaveLength(50)
    await click(m.root, 'project-device-load-more')
    expect(nodes(m.root, 'option')).toHaveLength(100)
    await click(m.root, 'project-device-load-more')
    expect(nodes(m.root, 'option')).toHaveLength(130)
    expect(findByTestId(m.root, 'project-device-load-more')).toBeUndefined()
    const id = devices(130, 1)[0].id
    const selectNode = nodes(m.root, 'select')[0]
    ;(selectNode.props!['onUpdate:modelValue'] as (id: string) => void)(id)
    expect(m.changed).toHaveBeenCalledWith(id)
    expect(vi.mocked(getDeviceArchivePage).mock.calls.map(([query]) => [query.selectionProjectId, query.pageNo])).toEqual([
      ['84459', 1], ['84459', 2], ['84459', 3]
    ])
  } finally { m.app.unmount() }
})

it('reuses the first empty search when opening the dropdown while it is loading or after it has loaded', async () => {
  let finishFirst!: (page: any) => void
  vi.mocked(getDeviceArchivePage).mockImplementationOnce(() => new Promise(resolve => { finishFirst = resolve }))
  const m = render()
  try {
    await flush()
    await m.state.search('')
    expect(getDeviceArchivePage).toHaveBeenCalledTimes(1)
    finishFirst({ list: devices(1, 50), total: 130 })
    await flush()
    expect(nodes(m.root, 'option')).toHaveLength(50)
    await m.state.search('')
    expect(getDeviceArchivePage).toHaveBeenCalledTimes(1)
    expect(nodes(m.root, 'option')).toHaveLength(50)
    await m.state.search('SN130')
    expect(getDeviceArchivePage).toHaveBeenCalledTimes(2)
    await m.state.search('')
    expect(getDeviceArchivePage).toHaveBeenCalledTimes(3)
  } finally { m.app.unmount() }
})

it('retries a failed first empty search instead of treating it as a loaded candidate list', async () => {
  vi.mocked(getDeviceArchivePage).mockRejectedValueOnce(new Error('unavailable'))
  const m = render()
  try {
    await flush()
    expect(textOf(m.root)).toContain('加载失败')
    await m.state.search('')
    expect(getDeviceArchivePage).toHaveBeenCalledTimes(2)
    expect(nodes(m.root, 'option')).toHaveLength(50)
  } finally { m.app.unmount() }
})

it('resets paging for an SN search and ignores an older page response', async () => {
  const m = render()
  try {
    await flush()
    let finishOld!: (page: any) => void
    vi.mocked(getDeviceArchivePage).mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    const old = m.state.loadMore()
    vi.mocked(getDeviceArchivePage).mockResolvedValueOnce({ list: devices(130, 1), total: 1 })
    await m.state.search(' SN130 ')
    finishOld({ list: devices(51, 50), total: 130 })
    await old; await flush()
    expect(nodes(m.root, 'option').map(node => node.props?.value)).toEqual([devices(130, 1)[0].id])
    expect(getDeviceArchivePage).toHaveBeenLastCalledWith({ selectionProjectId: '84459', pageNo: 1, pageSize: 50, keyword: 'SN130' })
    expect(m.state.loading).toBe(false)
  } finally { m.app.unmount() }
})

it('searches the project candidates by model beyond the initial page and retains that filter when loading more', async () => {
  const m = render()
  try {
    await flush()
    vi.mocked(getDeviceArchivePage).mockImplementation(async query => query.keyword === 'DPX-2000' && !query.sn && !query.productModel
      ? { list: devices(query.pageNo === 1 ? 101 : 151, query.pageNo === 1 ? 50 : 2)
        .map(device => ({ ...device, productModel: 'DPX-2000' })), total: 52 }
      : { list: [], total: 0 })
    await m.state.search(' DPX-2000 '); await flush()
    expect(nodes(m.root, 'option')).toHaveLength(50)
    expect(nodes(m.root, 'option')[0].props?.label).toContain('DPX-2000')
    await click(m.root, 'project-device-load-more')
    expect(nodes(m.root, 'option')).toHaveLength(52)
    const selectedId = devices(152, 1)[0].id
    ;(nodes(m.root, 'select')[0].props!['onUpdate:modelValue'] as (id: string) => void)(selectedId)
    expect(m.changed).toHaveBeenCalledWith(selectedId)
    expect(vi.mocked(getDeviceArchivePage).mock.calls.slice(-2).map(([query]) => query)).toEqual([
      { selectionProjectId: '84459', pageNo: 1, pageSize: 50, keyword: 'DPX-2000' },
      { selectionProjectId: '84459', pageNo: 2, pageSize: 50, keyword: 'DPX-2000' }
    ])
    expect(nodes(m.root, 'select')[0].props?.placeholder).toContain('型号')
  } finally { m.app.unmount() }
})

it('ignores the old serial search after entering a model and preserves the selected device ID', async () => {
  const chosen = devices(1, 1)[0]
  const m = render({ modelValue: chosen.id })
  try {
    await flush()
    let finishSerial!: (page: any) => void
    vi.mocked(getDeviceArchivePage).mockImplementationOnce(() => new Promise(resolve => { finishSerial = resolve }))
    const serialRequest = m.state.search('SN51')
    vi.mocked(getDeviceArchivePage).mockResolvedValue({ list: [{ ...devices(130, 1)[0], productModel: 'DPX-2000' }], total: 1 })
    await m.state.search('DPX-2000')
    finishSerial({ list: devices(51, 50), total: 130 })
    await serialRequest; await flush()
    expect(nodes(m.root, 'option').map(node => node.props?.value)).toEqual([chosen.id, devices(130, 1)[0].id])
    expect(nodes(m.root, 'select')[0].props?.['model-value']).toBe(chosen.id)
    expect(m.changed).not.toHaveBeenCalled()
    expect(m.state.loading).toBe(false)
  } finally { m.app.unmount() }
})

it('uses the same search box for model and serial, and retries an empty or failed keyword result', async () => {
  const m = render()
  try {
    await flush()
    vi.mocked(getDeviceArchivePage).mockRejectedValueOnce(new Error('unavailable'))
    await m.state.search('DPX-2000'); await flush()
    expect(textOf(m.root)).toContain('加载失败')
    vi.mocked(getDeviceArchivePage).mockResolvedValueOnce({ list: [], total: 0 })
    await click(m.root, 'project-device-retry')
    expect(getDeviceArchivePage).toHaveBeenLastCalledWith({ selectionProjectId: '84459', pageNo: 1, pageSize: 50, keyword: 'DPX-2000' })
    expect(nodes(m.root, 'option')).toHaveLength(0)
    expect(textOf(m.root)).toContain('已加载 0 / 0 台')
    await m.state.search('')
    expect(getDeviceArchivePage).toHaveBeenLastCalledWith({ selectionProjectId: '84459', pageNo: 1, pageSize: 50, keyword: undefined })
    expect(nodes(m.root, 'select')[0].props?.placeholder).toContain('序列号')
    await m.state.search('SN130')
    expect(getDeviceArchivePage).toHaveBeenLastCalledWith({ selectionProjectId: '84459', pageNo: 1, pageSize: 50, keyword: 'SN130' })
  } finally { m.app.unmount() }
})

it('keeps the first page on load-more failure and retries the same page', async () => {
  const m = render()
  try {
    await flush()
    vi.mocked(getDeviceArchivePage).mockRejectedValueOnce(new Error('unavailable'))
    await click(m.root, 'project-device-load-more')
    expect(nodes(m.root, 'option')).toHaveLength(50)
    expect(textOf(m.root)).toContain('加载失败')
    await click(m.root, 'project-device-retry')
    expect(nodes(m.root, 'option')).toHaveLength(100)
    expect(vi.mocked(getDeviceArchivePage).mock.calls.slice(-2).map(([query]) => query.pageNo)).toEqual([2, 2])
  } finally { m.app.unmount() }
})

it('does not silently treat an empty intermediate page as the whole candidate list', async () => {
  const m = render()
  try {
    await flush()
    vi.mocked(getDeviceArchivePage).mockResolvedValueOnce({ list: [], total: 130 })
    await m.state.loadMore(); await flush()
    expect(nodes(m.root, 'option')).toHaveLength(50)
    expect(textOf(m.root)).toContain('加载失败')
    expect(m.state.pageNo).toBe(1)
  } finally { m.app.unmount() }
})

it('clears old options when the project changes and ignores its late response', async () => {
  const m = render()
  try {
    await flush()
    let finishOld!: (page: any) => void
    vi.mocked(getDeviceArchivePage).mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    const old = m.state.loadMore()
    vi.mocked(getDeviceArchivePage).mockResolvedValueOnce({ list: [], total: 0 })
    ;(m.vm as any).$.props.projectId = '84447'
    await flush()
    finishOld({ list: devices(51, 50), total: 130 })
    await old; await flush()
    expect(nodes(m.root, 'option')).toHaveLength(0)
    expect(getDeviceArchivePage).toHaveBeenLastCalledWith({ selectionProjectId: '84447', pageNo: 1, pageSize: 50, keyword: undefined })
  } finally { m.app.unmount() }
})

it.each([undefined, '', 0, 'invalid', 9007199254740992])('never queries unrestricted candidates for invalid project %s', async projectId => {
  const m = render({ projectId })
  try {
    await flush()
    expect(getDeviceArchivePage).not.toHaveBeenCalled()
    expect(nodes(m.root, 'option')).toHaveLength(0)
    expect(nodes(m.root, 'select')[0].props?.disabled).toBe(true)
  } finally { m.app.unmount() }
})
