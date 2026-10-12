// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import Panel from './DeviceSoftwareVersionMaintenancePanel.vue'
import {
  getSoftwareVersionCurrent,
  getSoftwareVersionHistory,
  registerSoftwareVersion
} from '@/api/pms/asset/device'
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/components/DeviceCollection/ProjectDeviceLogArchiveDialog.vue', () => ({
  default: { props: ['modelValue', 'projectId', 'readonly'], template: '<div v-if="modelValue" data-log-project>{{ projectId }} / {{ readonly }}</div>' }
}))
vi.mock('@/api/pms/asset/device', () => ({
  getSoftwareVersionCurrent: vi.fn(),
  getSoftwareVersionHistory: vi.fn(),
  registerSoftwareVersion: vi.fn()
}))
let wrapper: ReturnType<typeof mount> | undefined
beforeEach(() => {
  vi.mocked(getSoftwareVersionCurrent)
    .mockReset()
    .mockResolvedValue({
      values: {
        conpVersion: 'BEFORE',
        bootVersion: '',
        cpldVersion: null,
        pcbVersion: 'P',
        customized: false
      },
      version: 2,
      canMaintain: true,
      recordedAt: 1700000000123
    })
  vi.mocked(getSoftwareVersionHistory).mockReset().mockResolvedValue({ list: [], total: 0 })
  vi.mocked(registerSoftwareVersion)
    .mockReset()
    .mockResolvedValue({ recordId: 99, replayed: false })
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})
const render = () =>
  mount(Panel, {
    attachTo: document.body,
    props: { deviceId: 500, projectId: 100 },
    global: { plugins: [ElementPlus], stubs: { transition: false } }
  })
it('opens the existing log archive for the actual project and closes it when the device changes', async () => {
  wrapper = render()
  await flushPromises()
  await wrapper.findAll('button').find((button) => button.text() === '项目日志档案')!.trigger('click')
  expect(wrapper.find('[data-log-project]').text()).toBe('100 / false')
  await wrapper.setProps({ deviceId: 501, projectId: 101 })
  expect(wrapper.find('[data-log-project]').exists()).toBe(false)
})
it('uses the existing history panel and hides manual actions when the server denies maintenance', async () => {
  vi.mocked(getSoftwareVersionCurrent).mockResolvedValueOnce({
    values: null,
    version: 0,
    canMaintain: false
  })
  wrapper = render()
  await flushPromises()
  expect(getSoftwareVersionHistory).toHaveBeenCalledWith(500, { pageNo: 1, pageSize: 20 })
  expect(wrapper.text()).toContain('软件版本历史')
  expect(wrapper.text()).not.toContain('维护软件版本')
})
it('saves the observed version and named values, then refreshes current and existing history', async () => {
  wrapper = render()
  await flushPromises()
  await wrapper
    .findAll('button')
    .find((button) => button.text() === '维护软件版本')!
    .trigger('click')
  await flushPromises()
  await wrapper.find('.el-dialog .el-form-item input').setValue('AFTER')
  vi.mocked(getSoftwareVersionCurrent).mockResolvedValueOnce({
    values: { conpVersion: 'AFTER' },
    version: 3,
    canMaintain: true
  })
  await wrapper
    .findAll('.el-dialog button')
    .find((button) => button.text() === '保存')!
    .trigger('click')
  await flushPromises()
  expect(registerSoftwareVersion).toHaveBeenCalledWith(
    500,
    expect.objectContaining({
      projectId: 100,
      expectedVersion: 2,
      requestKey: expect.any(String),
      values: expect.objectContaining({
        conpVersion: 'AFTER',
        bootVersion: '',
        cpldVersion: null,
        customized: false
      })
    })
  )
  expect(getSoftwareVersionHistory).toHaveBeenCalledTimes(2)
  expect(wrapper.emitted('updated')).toHaveLength(1)
  expect(
    wrapper.findAll('input').map((input) => (input.element as HTMLInputElement).value)
  ).toContain('AFTER')
})
it('does not expose a late current response from a previously selected device', async () => {
  let finish: (value: any) => void = () => {}
  vi.mocked(getSoftwareVersionCurrent).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve
      })
  )
  wrapper = render()
  await wrapper.setProps({ deviceId: 501 })
  await flushPromises()
  finish({ values: { conpVersion: 'STALE-DEVICE' }, version: 1, canMaintain: true })
  await flushPromises()
  expect(
    wrapper.findAll('input').map((input) => (input.element as HTMLInputElement).value)
  ).not.toContain('STALE-DEVICE')
  expect(getSoftwareVersionHistory).toHaveBeenCalledWith(501, { pageNo: 1, pageSize: 20 })
})
it('does not refresh or announce an old-device save after selecting another device', async () => {
  let finish: (value: any) => void = () => {}
  vi.mocked(registerSoftwareVersion).mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  wrapper = render()
  await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === '维护软件版本')!.trigger('click')
  await flushPromises()
  await wrapper.findAll('.el-dialog button').find(button => button.text() === '保存')!.trigger('click')
  await wrapper.setProps({ deviceId: 501 })
  await flushPromises()
  const currentCalls = vi.mocked(getSoftwareVersionCurrent).mock.calls.length
  finish({ recordId: 99, replayed: false })
  await flushPromises()
  expect(getSoftwareVersionCurrent).toHaveBeenCalledTimes(currentCalls)
  expect(wrapper.emitted('updated')).toBeUndefined()
})
