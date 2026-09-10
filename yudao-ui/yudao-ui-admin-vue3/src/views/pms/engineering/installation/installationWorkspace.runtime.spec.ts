import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Installation from './index.vue'
import * as InstallationApi from '@/api/pms/engineering/installation'
import { mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/asset/equipment', () => ({ __v_isRef: false, getEquipmentPage: vi.fn() }))
vi.mock('@/api/pms/engineering/installation', () => ({ getInstallationPage: vi.fn(), createInstallation: vi.fn(), updateInstallation: vi.fn(), deleteInstallation: vi.fn() }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_ENG_STATUS: 'installation' }, getIntDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), error: vi.fn(), delConfirm: vi.fn() }) }))
const formStub = defineComponent({ setup(_, { slots, attrs, expose }) {
  expose({ validate: async () => true })
  return () => h('form', attrs, slots.default?.())
} })
const editorStub = defineComponent({ setup(_, { attrs }) { return () => h('editor', attrs) } })
const imageStub = defineComponent({ setup(_, { attrs }) { return () => h('photo', attrs) } })
const nodes = (node: TestNode, type: string): TestNode[] => [...(node.type === type ? [node] : []), ...node.children.flatMap(child => nodes(child, type))]
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
const render = () => {
  const mounted = mount(Installation, {}, { ElTable: passthrough, ElTableColumn: tableColumn, ElForm: formStub, ElRow: passthrough, ElCol: passthrough, ElInput: passthrough, ElDatePicker: passthrough, ElSelect: passthrough, ElOption: passthrough, PmsEntitySelect: passthrough, PmsLocationSelector: passthrough, EquipmentTag: passthrough, Editor: editorStub, UploadImg: imageStub })
  return { ...mounted, state: (mounted.vm as any).$.setupState }
}
const completed = { id: 8, projectId: 1, code: 'INS-COMPLETE', status: 2, version: 3, siteId: 20, siteVersion: 1, siteLocationId: 30, siteLocationVersion: 2, locationResolutionStatus: 'RESOLVED', photoUrl: 'historical-photo' }
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(InstallationApi.getInstallationPage).mockResolvedValue({ list: [], total: 0 })
})

it('keeps completed installation content, photo and location references read-only', async () => {
  const mounted = render()
  try {
    mounted.state.openForm(completed); await flush()
    expect(mounted.state.readOnly).toBe(true)
    expect(nodes(mounted.root, 'editor').every(node => node.props?.readonly === true)).toBe(true)
    expect(nodes(mounted.root, 'photo')[0].props?.disabled).toBe(true)
    expect(mounted.state.form.locationMaintenance.siteLocation).toEqual({ id: 30, expectedVersion: 2 })
    await mounted.state.save(); await mounted.state.remove(completed)
    expect(InstallationApi.updateInstallation).not.toHaveBeenCalled()
    expect(InstallationApi.deleteInstallation).not.toHaveBeenCalled()
  } finally { mounted.app.unmount() }
})

it('starts a clean pending installation and emits the existing millisecond date contract', async () => {
  const mounted = render()
  try {
    mounted.state.openForm(completed)
    mounted.state.openForm(); await flush()
    expect(mounted.state.form.status).toBe(0)
    expect(mounted.state.form.siteId).toBeUndefined()
    expect(mounted.state.form.photoUrl).toBe('')
    expect(mounted.state.form.installTime).toBeUndefined()
    Object.assign(mounted.state.form, { projectId: 1, code: 'INS-NEW', installTime: '1788055200000', locationMaintenance: { projectId: 1, fallbackLocation: '测试现场' } })
    await mounted.state.save()
    expect(InstallationApi.createInstallation).toHaveBeenCalledWith(expect.objectContaining({ installTime: 1788055200000, status: 0, installLocation: '测试现场', locationMaintenance: undefined }))
    mounted.state.form.installTime = null
    expect(mounted.state.savePayload().installTime).toBeUndefined()
  } finally { mounted.app.unmount() }
})
