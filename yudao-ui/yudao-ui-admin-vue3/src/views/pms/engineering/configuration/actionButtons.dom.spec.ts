// @vitest-environment happy-dom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { computed, defineComponent, h, inject, provide } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import Configuration from './with-device-logs.vue'
import JointTest from '../joint-test/with-device-logs.vue'

const api = vi.hoisted(() => ({ configurationPage: vi.fn(), jointPage: vi.fn(), startConfiguration: vi.fn(), startJoint: vi.fn(), confirm: vi.fn() }))
vi.mock('../joint-test/resolveLogTask', () => ({ resolveJointLogTask: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: (permissions: string[]) => !permissions.some(value => value.endsWith(':delete')) }))
vi.mock('@/api/pms/engineering/configuration', () => ({ getConfigurationPage: api.configurationPage, startConfiguration: api.startConfiguration }))
vi.mock('@/api/pms/engineering/joint-test', () => ({ getJointTestPage: api.jointPage, startJointTest: api.startJoint }))
vi.mock('@/api/pms/project/projects', () => ({ getProjectPage: vi.fn() }))
vi.mock('@/api/system/user', () => ({ getUserPage: vi.fn() }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 3, nickname: 'Self' } }) }))
vi.mock('@/api/pms/asset/device/archive', () => ({ getDeviceArchiveRecord: vi.fn() }))
vi.mock('@/api/pms/asset/device-log-archive', () => ({ download: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: { download: vi.fn() } }))
vi.mock('@/utils/download', () => ({ default: { file: vi.fn() } }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getIntDictOptions: () => [] }))
vi.mock('@/utils/formatTime', () => ({ dateFormatter: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), confirm: api.confirm }) }))
vi.mock('@/components/DeviceCollection/CollectionWithDeviceLogsDialog.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/DeviceCollection/ProjectDeviceLogArchiveDialog.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/DeviceCollection/BusinessCollectionLogs.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/ProjectDeviceSelect/index.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/DeviceTag/index.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/UserTag/index.vue', () => ({ default: { render: () => null } }))

const passthrough = defineComponent({ setup(_, { slots }) { return () => h('div', slots.default?.()) } })
const table = defineComponent({ props: ['data'], setup(props, { slots }) {
  provide('action-test-rows', computed(() => props.data || []))
  return () => h('div', slots.default?.())
} })
const column = defineComponent({ setup(_, { slots }) {
  const rows = inject<any>('action-test-rows')
  return () => h('div', rows.value.map((row: unknown) => slots.default?.({ row })))
} })
const button = defineComponent({ inheritAttrs: false, setup(_, { attrs, slots }) { return () => h('button', attrs, slots.default?.()) } })
const wrappers: ReturnType<typeof mount>[] = []
beforeEach(() => { vi.resetAllMocks(); api.confirm.mockResolvedValue(undefined) })
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

it.each([
  { label: 'configuration', component: Configuration, start: '开始调试', expected: ['完成调试'], page: api.configurationPage, action: api.startConfiguration },
  { label: 'joint test', component: JointTest, start: '开始联调', expected: ['联调通过', '联调失败'], page: api.jointPage, action: api.startJoint }
])('keeps $label next actions after start when delete permission removes its DOM node', async ({ component, start, expected, page, action }) => {
  page.mockResolvedValueOnce({ list: [{ id: 8, projectId: 1, equipmentId: '123', status: 0, version: 0 }], total: 1 })
    .mockResolvedValue({ list: [{ id: 8, projectId: 1, equipmentId: '123', status: 1, version: 1 }], total: 1 })
  action.mockResolvedValue(undefined)
  const errors: unknown[] = []
  const wrapper = mount(component, { props: { projectId: 1 }, attachTo: document.body, global: {
    config: { errorHandler: error => errors.push(error) },
    directives: {
      hasPermi: (el, binding) => { if (binding.value.some((permission: string) => permission.endsWith(':delete'))) el.parentNode?.removeChild(el) },
      loading: {}, 'dompurify-html': {}
    },
    stubs: { PmsEntitySelect: true, ElRow: passthrough, ElCol: passthrough, ElInput: true, ElDatePicker: true, ElRadio: true, ElRadioGroup: true, Editor: true, UploadFile: true, ContentWrap: passthrough, ElForm: passthrough, ElFormItem: passthrough, ElSelect: passthrough, ElOption: passthrough, ElAlert: true, ElTable: table, ElTableColumn: column, ElButton: button, Pagination: true, Dialog: true, Icon: true, DictTag: true }
  } })
  wrappers.push(wrapper)
  await flushPromises()
  const startButton = wrapper.findAll('button').find(node => node.text() === start)
  expect(startButton).toBeDefined()
  expect(wrapper.findAll('button').some(node => node.text() === '删除')).toBe(false)
  await startButton!.trigger('click'); await flushPromises()
  expect(action).toHaveBeenCalledWith(8)
  expect(page).toHaveBeenCalledTimes(2)
  expect(errors.map(String)).toEqual([])
  for (const label of expected) expect(wrapper.findAll('button').some(node => node.text() === label)).toBe(true)
  expect(wrapper.findAll('button').some(node => node.text() === '删除')).toBe(false)
})
