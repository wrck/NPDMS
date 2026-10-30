import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import DeviceTag from './index.vue'
import * as DeviceApi from '@/api/pms/asset/device'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/asset/device/archive', () => ({ getDeviceArchiveRecord: vi.fn() }))
vi.mock('@/api/pms/asset/device', () => ({ getDevice: vi.fn() }))
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
beforeEach(() => vi.clearAllMocks())

it('preserves a long string identifier when querying and displaying the device', async () => {
  const id = '970000000000090104'
  vi.mocked(DeviceApi.getDevice).mockResolvedValue({ summary: { name: '测试设备' } } as any)
  const mounted = mount(DeviceTag, { deviceId: id })
  try {
    await flush()
    expect(DeviceApi.getDevice).toHaveBeenCalledWith(id)
    expect(textOf(mounted.root)).toBe('测试设备')
  } finally { mounted.app.unmount() }
})

it('does not let an old row response replace the newly selected device name', async () => {
  let finishOld!: (value: unknown) => void
  vi.mocked(DeviceApi.getDevice).mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    .mockResolvedValueOnce({ summary: { name: '当前设备' } } as any)
  const selected = ref<number | string>('970000000000090104')
  const wrapper = defineComponent({ setup: () => () => h(DeviceTag, { deviceId: selected.value }) })
  const mounted = mount(wrapper)
  try {
    selected.value = 1001
    await flush()
    expect(DeviceApi.getDevice).toHaveBeenLastCalledWith(1001)
    finishOld({ summary: { name: '旧设备' } })
    await flush()
    expect(textOf(mounted.root)).toBe('当前设备')
  } finally { mounted.app.unmount() }
})

it('uses the current product name when the device has no custom name', async () => {
  vi.mocked(DeviceApi.getDevice).mockResolvedValue({ summary: { productName: '当前产品', sn: 'SN' } } as any)
  const mounted = mount(DeviceTag, { deviceId: 901 })
  try { await flush(); expect(textOf(mounted.root)).toBe('当前产品') }
  finally { mounted.app.unmount() }
})
