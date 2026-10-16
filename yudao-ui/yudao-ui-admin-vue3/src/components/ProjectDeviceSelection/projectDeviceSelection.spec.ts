import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Selector from './index.vue'
import { findByTestId, mount, textOf } from '@/views/pms/cutover/cutoverTestHarness'

const api = vi.hoisted(() => ({ getProjectDevicePage: vi.fn() }))
vi.mock('@/api/pms/project/selection', () => api)
const device = {
  deviceId: '401',
  serialNumber: 'SN-401',
  productCode: 'P-401',
  productModel: 'MODEL',
  currentSoftwareVersion: 'R-123'
}
const flush = async () => {
  for (let i = 0; i < 12; i++) await nextTick()
}
beforeEach(() => api.getProjectDevicePage.mockReset())

it('shares paged selection, deduplicates and removes selected devices', async () => {
  api.getProjectDevicePage.mockResolvedValue({ list: [device], total: 1 })
  const selected = ref<(typeof device)[]>([])
  const Host = defineComponent({
    setup: () => () =>
      h(Selector, {
        projectId: '101',
        modelValue: selected.value,
        'onUpdate:modelValue': (value) => {
          selected.value = value as (typeof device)[]
        }
      })
  })
  const view = mount(Host)
  await (findByTestId(view.root, 'choose-project-devices')!.props!.onClick as Function)()
  await flush()
  const add = () =>
    (findByTestId(view.root, 'add-project-device-401')!.props!.onClick as Function)()
  add()
  await flush()
  add()
  await flush()
  expect(selected.value).toEqual([device])
  expect(textOf(view.root)).toContain('P-401')
  expect(textOf(view.root)).toContain('R-123')
  await (findByTestId(view.root, 'remove-create-device-401')!.props!.onClick as Function)()
  await flush()
  expect(selected.value).toEqual([])
  view.app.unmount()
})

it('does not add a partially matched serial batch or a response from the previous project', async () => {
  const selected = ref<(typeof device)[]>([])
  const project = ref('101')
  const Host = defineComponent({
    setup: () => () =>
      h(Selector, {
        projectId: project.value,
        modelValue: selected.value,
        'onUpdate:modelValue': (value) => {
          selected.value = value as (typeof device)[]
        }
      })
  })
  const view = mount(Host)
  api.getProjectDevicePage
    .mockResolvedValueOnce({ list: [device], total: 1 })
    .mockResolvedValueOnce({ list: [], total: 0 })
  const input = () => findByTestId(view.root, 'create-serials')!
  ;(input().props!['onUpdate:modelValue'] as Function)('SN-401,MISSING')
  await flush()
  await (findByTestId(view.root, 'resolve-create-serials')!.props!.onClick as Function)()
  await flush()
  expect(selected.value).toEqual([])
  expect(textOf(view.root)).toContain('本次未添加任何设备')
  let finish!: (value: unknown) => void
  api.getProjectDevicePage.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve
      })
  )
  ;(input().props!['onUpdate:modelValue'] as Function)('SN-401')
  await flush()
  const pending = (findByTestId(view.root, 'resolve-create-serials')!.props!.onClick as Function)()
  project.value = '102'
  await flush()
  finish({ list: [device], total: 1 })
  await pending
  await flush()
  expect(selected.value).toEqual([])
  view.app.unmount()
})
