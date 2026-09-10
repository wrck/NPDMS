import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import EquipmentTag from './index.vue'
import * as EquipmentApi from '@/api/pms/asset/equipment'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/asset/equipment', () => ({ getEquipment: vi.fn() }))
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
beforeEach(() => vi.clearAllMocks())

it('preserves a long string identifier when querying and displaying the device', async () => {
  const id = '970000000000090104'
  vi.mocked(EquipmentApi.getEquipment).mockResolvedValue({ name: '测试设备' })
  const mounted = mount(EquipmentTag, { equipmentId: id })
  try {
    await flush()
    expect(EquipmentApi.getEquipment).toHaveBeenCalledWith(id)
    expect(textOf(mounted.root)).toBe('测试设备')
  } finally { mounted.app.unmount() }
})

it('does not let an old row response replace the newly selected device name', async () => {
  let finishOld!: (value: unknown) => void
  vi.mocked(EquipmentApi.getEquipment).mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    .mockResolvedValueOnce({ name: '当前设备' })
  const selected = ref<number | string>('970000000000090104')
  const wrapper = defineComponent({ setup: () => () => h(EquipmentTag, { equipmentId: selected.value }) })
  const mounted = mount(wrapper)
  try {
    selected.value = 1001
    await flush()
    expect(EquipmentApi.getEquipment).toHaveBeenLastCalledWith(1001)
    finishOld({ name: '旧设备' })
    await flush()
    expect(textOf(mounted.root)).toBe('当前设备')
  } finally { mounted.app.unmount() }
})
