import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import DeviceTag from './index.vue'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/asset/device/archive', () => ({ getDeviceArchiveRecord: vi.fn() }))
const actor = vi.hoisted(() => ({ userId: 42, tenantId: 7, visitTenantId: undefined as number | undefined }))
vi.mock('@/utils/auth', () => ({ getCurrentUserId: () => actor.userId, getTenantId: () => actor.tenantId, getVisitTenantId: () => actor.visitTenantId }))
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
beforeEach(() => { vi.clearAllMocks(); actor.userId = 42; actor.tenantId = 7; actor.visitTenantId = undefined })

it('shares only an in-flight name read between a list and its readonly detail', async () => {
  let release!: (value: unknown) => void
  vi.mocked(DeviceArchiveApi.getDeviceArchiveRecord).mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
    .mockResolvedValue({ name: '当前设备' })
  const first = mount(DeviceTag, { deviceId: '1001' }), second = mount(DeviceTag, { deviceId: 1001 })
  try {
    expect(DeviceArchiveApi.getDeviceArchiveRecord).toHaveBeenCalledTimes(1)
    release({ name: '当前设备' }); await flush()
    expect(textOf(first.root)).toBe('当前设备'); expect(textOf(second.root)).toBe('当前设备')
    const next = mount(DeviceTag, { deviceId: 1001 })
    try { await flush(); expect(DeviceArchiveApi.getDeviceArchiveRecord).toHaveBeenCalledTimes(2) }
    finally { next.app.unmount() }
  } finally { release({ name: '当前设备' }); await flush(); first.app.unmount(); second.app.unmount() }
})

it.each(['userId', 'tenantId', 'visitTenantId'] as const)('never shares an in-flight device read across %s', async field => {
  let release!: (value: unknown) => void
  vi.mocked(DeviceArchiveApi.getDeviceArchiveRecord).mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
    .mockResolvedValueOnce({ name: '当前上下文设备' })
  const previous = mount(DeviceTag, { deviceId: 1002 })
  actor[field] = 99
  const current = mount(DeviceTag, { deviceId: 1002 })
  try {
    await flush()
    expect(DeviceArchiveApi.getDeviceArchiveRecord).toHaveBeenCalledTimes(2)
    expect(textOf(current.root)).toBe('当前上下文设备')
  } finally { release({ name: '原上下文设备' }); await flush(); previous.app.unmount(); current.app.unmount() }
})

it('does not retain a failed shared device read for the next entry', async () => {
  vi.mocked(DeviceArchiveApi.getDeviceArchiveRecord).mockRejectedValueOnce(new Error('read denied'))
    .mockResolvedValueOnce({ name: '重试设备' })
  const denied = mount(DeviceTag, { deviceId: 1003 })
  await flush()
  const retried = mount(DeviceTag, { deviceId: 1003 })
  try {
    await flush()
    expect(DeviceArchiveApi.getDeviceArchiveRecord).toHaveBeenCalledTimes(2)
    expect(textOf(denied.root)).toBe('设备#1003')
    expect(textOf(retried.root)).toBe('重试设备')
  } finally { denied.app.unmount(); retried.app.unmount() }
})

it('preserves a long string identifier when querying and displaying the device', async () => {
  const id = '970000000000090104'
  vi.mocked(DeviceArchiveApi.getDeviceArchiveRecord).mockResolvedValue({ name: '测试设备' })
  const mounted = mount(DeviceTag, { deviceId: id })
  try {
    await flush()
    expect(DeviceArchiveApi.getDeviceArchiveRecord).toHaveBeenCalledWith(id)
    expect(textOf(mounted.root)).toBe('测试设备')
  } finally { mounted.app.unmount() }
})

it('does not let an old row response replace the newly selected device name', async () => {
  let finishOld!: (value: unknown) => void
  vi.mocked(DeviceArchiveApi.getDeviceArchiveRecord).mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    .mockResolvedValueOnce({ name: '当前设备' })
  const selected = ref<number | string>('970000000000090104')
  const wrapper = defineComponent({ setup: () => () => h(DeviceTag, { deviceId: selected.value }) })
  const mounted = mount(wrapper)
  try {
    selected.value = 1001
    await flush()
    expect(DeviceArchiveApi.getDeviceArchiveRecord).toHaveBeenLastCalledWith(1001)
    finishOld({ name: '旧设备' })
    await flush()
    expect(textOf(mounted.root)).toBe('当前设备')
  } finally { mounted.app.unmount() }
})
