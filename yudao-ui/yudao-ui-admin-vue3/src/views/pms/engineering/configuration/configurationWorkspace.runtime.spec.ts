import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Configuration from './index.vue'
import * as ConfigurationApi from '@/api/pms/engineering/configuration'
import { mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/pms/project/project', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/asset/equipment', () => ({ __v_isRef: false, getEquipmentPage: vi.fn() }))
vi.mock('@/components/EquipmentTag/index.vue', () => ({ default: { render: () => null } }))
vi.mock('@/api/pms/engineering/configuration', () => ({ getConfigurationPage: vi.fn(), createConfiguration: vi.fn(), updateConfiguration: vi.fn(), deleteConfiguration: vi.fn() }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_ENG_STATUS: 'configuration' }, getIntDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), delConfirm: vi.fn() }) }))
const formStub = defineComponent({ setup(_, { slots, attrs, expose }) {
  expose({ validate: async () => true })
  return () => h('form', attrs, slots.default?.())
} })
const editorStub = defineComponent({ setup(_, { attrs }) { return () => h('editor', attrs) } })
const uploaderStub = defineComponent({ setup(_, { attrs }) { return () => h('uploader', attrs) } })
const nodes = (node: TestNode, type: string): TestNode[] => [...(node.type === type ? [node] : []), ...node.children.flatMap(child => nodes(child, type))]
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
const render = () => {
  const mounted = mount(Configuration, {}, { ElTable: passthrough, ElTableColumn: tableColumn, ElForm: formStub, ElRow: passthrough, ElCol: passthrough, ElInput: passthrough, ElDatePicker: passthrough, ElSelect: passthrough, ElOption: passthrough, PmsEntitySelect: passthrough, Editor: editorStub, UploadFile: uploaderStub })
  return { ...mounted, state: (mounted.vm as any).$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(ConfigurationApi.getConfigurationPage).mockResolvedValue({ list: [], total: 0 })
})

it('keeps completed local results and their logs read-only', async () => {
  const mounted = render()
  try {
    const completed = { id: 8, projectId: 1, code: 'CFG-COMPLETE', status: 2, version: 3, configLogUrl: 'historical-log' }
    mounted.state.openForm(completed); await flush()
    expect(mounted.state.readOnly).toBe(true)
    expect(nodes(mounted.root, 'editor')[0].props?.readonly).toBe(true)
    expect(nodes(mounted.root, 'uploader')[0].props?.disabled).toBe(true)
    await mounted.state.save(); await mounted.state.remove(completed)
    expect(ConfigurationApi.updateConfiguration).not.toHaveBeenCalled()
    expect(ConfigurationApi.deleteConfiguration).not.toHaveBeenCalled()
  } finally { mounted.app.unmount() }
})

it('does not copy an old result or log into a new pending record', async () => {
  const mounted = render()
  try {
    mounted.state.openForm({ id: 8, projectId: 1, code: 'OLD', status: 2, version: 3, configLogUrl: 'historical-log', configSnapshot: 'historical snapshot' })
    mounted.state.openForm(); await flush()
    Object.assign(mounted.state.form, { projectId: 1, code: 'NEW', equipmentId: '970000000000090104', debugTime: '1788055200000' })
    await mounted.state.save()
    expect(ConfigurationApi.createConfiguration).toHaveBeenCalledWith(expect.objectContaining({ status: 0, configLogUrl: '', version: undefined, debugTime: 1788055200000, equipmentId: '970000000000090104' }))
    expect(vi.mocked(ConfigurationApi.createConfiguration).mock.calls[0][0].configSnapshot).toBeUndefined()
  } finally { mounted.app.unmount() }
})

it('does not turn a cleared optional date into the Unix epoch', async () => {
  const mounted = render()
  try {
    mounted.state.openForm(); await flush()
    Object.assign(mounted.state.form, { projectId: 1, code: 'NO-DATE', debugTime: null })
    await mounted.state.save()
    expect(vi.mocked(ConfigurationApi.createConfiguration).mock.calls[0][0].debugTime).toBeUndefined()
  } finally { mounted.app.unmount() }
})

it('rejects the unselected project sentinel while accepting actual project identifiers', () => {
  const mounted = render()
  try {
    const callback = vi.fn()
    mounted.state.rules.projectId[1].validator({}, 0, callback)
    expect(callback).toHaveBeenLastCalledWith(expect.any(Error))
    mounted.state.rules.projectId[1].validator({}, '992203060001', callback)
    expect(callback).toHaveBeenLastCalledWith(undefined)
  } finally { mounted.app.unmount() }
})

it('keeps the string upload contract when reopening a record with no log', async () => {
  const mounted = render()
  try {
    mounted.state.openForm({ id: 9, projectId: 1, code: 'CFG-NO-LOG', status: 0, version: 1, configLogUrl: null })
    await flush()
    const uploader = nodes(mounted.root, 'uploader')[0]
    expect(uploader.props?.modelValue).toBe('')
    const emitValue = uploader.props?.['onUpdate:modelValue'] as (value: string) => void
    emitValue('local-log-url')
    await mounted.state.save()
    expect(ConfigurationApi.updateConfiguration).toHaveBeenCalledWith(expect.objectContaining({ configLogUrl: 'local-log-url', status: 0, version: 1 }))
  } finally { mounted.app.unmount() }
})
