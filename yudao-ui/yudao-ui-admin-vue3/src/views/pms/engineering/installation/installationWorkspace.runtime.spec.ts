import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Installation from './index.vue'
import * as InstallationApi from '@/api/pms/engineering/installation'
import { mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/asset/device/archive', () => ({ __v_isRef: false, getDeviceArchivePage: vi.fn() }))
vi.mock('@/api/pms/engineering/installation', () => ({ getInstallationPage: vi.fn(), createInstallation: vi.fn(), updateInstallation: vi.fn(), deleteInstallation: vi.fn() }))
vi.mock('@/api/system/user', () => ({ __v_isRef: false, getSimpleUserList: vi.fn().mockResolvedValue([{ id: 9, nickname: '当前用户' }]) }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 9 } }) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_ENG_STATUS: 'installation' }, getIntDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), error: vi.fn(), delConfirm: vi.fn() }) }))
const formStub = defineComponent({ setup(_, { slots, attrs, expose }) {
  expose({ validate: async () => true })
  return () => h('form', attrs, slots.default?.())
} })
const imageStub = defineComponent({ setup(_, { attrs }) { return () => h('photo', attrs) } })
const nodes = (node: TestNode, type: string): TestNode[] => [...(node.type === type ? [node] : []), ...node.children.flatMap(child => nodes(child, type))]
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
const render = () => {
  const mounted = mount(Installation, {}, { ElTable: passthrough, ElTableColumn: tableColumn, ElForm: formStub, ElRow: passthrough, ElCol: passthrough, ElInput: passthrough, ElSelect: passthrough, ElOption: passthrough, PmsEntitySelect: passthrough, ProjectDeviceSelect: passthrough, DeviceTag: passthrough, UploadImg: imageStub })
  return { ...mounted, state: (mounted.vm as any).$.setupState }
}
const completed = { id: 8, projectId: 1, code: 'INS-COMPLETE', status: 2, version: 3, installLocation: '历史站点/机房A', installTime: 1788055200000, photoUrl: 'historical-photo' }
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(InstallationApi.getInstallationPage).mockResolvedValue({ list: [], total: 0 })
})

it('keeps completed installation content, site text and photo read-only', async () => {
  const mounted = render()
  try {
    mounted.state.openForm(completed); await flush()
    expect(mounted.state.readOnly).toBe(true)
    expect(mounted.state.form.installLocation).toBe('历史站点/机房A')
    expect(mounted.state.form.installTime).toBe(1788055200000)
    expect(nodes(mounted.root, 'photo')[0].props?.disabled).toBe(true)
    await mounted.state.save(); await mounted.state.remove(completed)
    expect(InstallationApi.updateInstallation).not.toHaveBeenCalled()
    expect(InstallationApi.deleteInstallation).not.toHaveBeenCalled()
  } finally { mounted.app.unmount() }
})

it('starts a clean pending installation with the current-user installer, manual site and submission-time contract', async () => {
  const mounted = render()
  try {
    mounted.state.openForm(completed)
    mounted.state.openForm(); await flush()
    expect(mounted.state.form.status).toBe(0)
    expect(mounted.state.form.installerUserId).toBe(9)
    expect(mounted.state.form.photoUrl).toBe('')
    expect(mounted.state.form.installTime).toBeUndefined()
    Object.assign(mounted.state.form, { projectId: 1, code: 'INS-NEW', installLocation: '手动站点', installTime: '1788055200000' })
    await mounted.state.save()
    expect(InstallationApi.createInstallation).toHaveBeenCalledWith(expect.objectContaining({ installTime: 1788055200000, status: 0, installLocation: '手动站点', locationMaintenance: undefined }))
    mounted.state.form.installTime = null
    expect(mounted.state.savePayload().installTime).toBeUndefined()
  } finally { mounted.app.unmount() }
})
