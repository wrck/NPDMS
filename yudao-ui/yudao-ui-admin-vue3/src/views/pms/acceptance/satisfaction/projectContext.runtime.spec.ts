import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref, type Component } from 'vue'
import {
  mount,
  passthrough,
  tableColumn
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Workbench from './index.vue'
import CompletionWorkbench from '../completion-certificate/index.vue'

const api = vi.hoisted(() => ({ listTemplates: vi.fn() }))
const message = vi.hoisted(() => ({ confirm: vi.fn(), success: vi.fn(), warning: vi.fn() }))
const fileApi = vi.hoisted(() => ({ createAccessTicket: vi.fn() }))
const confirmations = vi.hoisted(() => ({ getConfirmationPage: vi.fn(), getCompletionCertificatePage: vi.fn() }))
const routeGuards = vi.hoisted(() => ({ leave: [] as Array<() => boolean>, update: [] as Array<() => boolean> }))
vi.mock('vue-router', () => ({
  onBeforeRouteLeave: (guard: () => boolean) => routeGuards.leave.push(guard),
  onBeforeRouteUpdate: (guard: () => boolean) => routeGuards.update.push(guard)
}))
vi.mock('@/api/pms/acceptance/satisfaction', () => api)
vi.mock('@/api/system/user', () => ({ __v_isRef: false, getUserPage: vi.fn() }))
vi.mock('@/api/system/user/profile', () => ({ getUserProfile: vi.fn() }))
vi.mock('@/api/pms/acceptance/completion-certificate', () => confirmations)
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/asset/device', () => ({ getDevicePage: vi.fn() }))
vi.mock('@/api/pms/commerce', () => ({ getDeliveryScopePage: vi.fn(), getSalesOrderLinePage: vi.fn() }))
vi.mock('@/api/pms/platform/dynamic-form', () => ({ getTemplateSelection: vi.fn() }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 1, nickname: '当前用户' } }) }))
vi.mock('@/components/BusinessEntity/DeliveryPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('@/api/infra/file', () => ({ updateFile: vi.fn() }))
vi.mock('@/components/FormCreate', () => ({ SignaturePad: { render: () => null } }))
vi.mock('@/components/FormCreate/src/customerConfirmation', () => ({ signaturePngFile: vi.fn() }))
vi.mock('../completion-certificate/ConfirmationLinkDialog.vue', () => ({ default: { render: () => null } }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_COMPLETION_CERT_STATUS: 'completion_status' }, getIntDictOptions: () => [] }))
vi.mock('../completion-certificate/ConfirmationPrintPreview.vue', () => ({ default: { setup: (_: any, { expose }: any) => { expose({ isExporting: () => false, close: () => true }); return () => null } } }))
vi.mock('../completion-certificate/ConfirmationPrintTemplates.vue', () => ({ default: { render: () => null } }))
vi.mock('../completion-certificate/confirmationPrintForm', () => ({ documentLabel: (kind: string) => kind === 'COMPLETION' ? '完工证明' : '满意度调查表', printCategory: (kind: string) => `${kind}_PRINT`, completionDisplay: (value: any) => value }))
vi.mock('@/api/pms/platform/file', () => fileApi)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/utils/auth', () => ({ getTenantId: () => 1 }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/components/Qrcode', () => ({ Qrcode: { render: () => null } }))
vi.mock('./TemplatePanel.vue', () => ({ default: { render: () => '模板管理测试占位' } }))

const mounted: { unmount: () => void }[] = []
const flush = async () => {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
const renderPanel = (component: Component, initial: { projectId?: number; readonly?: boolean } = {}) => {
  const props = reactive(initial)
  // setupState is used only to drive the real SFC handlers; the public integration API remains narrow.
  const child = ref<any>()
  const wrapper = defineComponent({ setup: () => () => h(component, { ...props, ref: child }) })
  const stubs = Object.fromEntries(
    [
      'ElInputNumber',
      'ElSelect',
      'ElOption',
      'ElSkeleton',
      'ElTable',
      'ElDialog',
      'ElInput',
      'ElDatePicker',
      'ElUpload',
      'ElCheckboxGroup',
      'ElCheckbox',
      'ElTabs',
      'ElTabPane'
      , 'PmsEntitySelect', 'ElCol', 'ElRow', 'ElRadio', 'ElRadioGroup', 'ElDrawer', 'ElDescriptions', 'ElDescriptionsItem', 'ElLink', 'ElEmpty'
    ].map((name) => [name, passthrough])
  )
  const { app, root } = mount(wrapper, {}, { ...stubs, ElTableColumn: tableColumn })
  mounted.push(app)
  return { props, root, child, state: () => child.value.$.setupState }
}

beforeEach(() => {
  vi.clearAllMocks()
  routeGuards.leave.length = 0
  routeGuards.update.length = 0
  confirmations.getConfirmationPage.mockResolvedValue({ list: [], total: 0 })
  confirmations.getCompletionCertificatePage.mockResolvedValue({ list: [], total: 0 })
  api.listTemplates.mockResolvedValue([
    { id: 20, name: '发布问卷', status: 'PUBLISHED', currentRevisionId: 21,
      revisions: [{ id: 21, status: 'PUBLISHED', revisionNo: 1, threshold: 80 }, { id: 22, status: 'DRAFT', revisionNo: 2, threshold: 80 }] },
    { id: 30, name: '停用问卷', status: 'DISABLED', currentRevisionId: 31,
      revisions: [{ id: 31, status: 'PUBLISHED', revisionNo: 1, threshold: 80 }] }
  ])
  message.confirm.mockResolvedValue(undefined)
  vi.stubGlobal('window', { clearTimeout, setTimeout, addEventListener: vi.fn(), removeEventListener: vi.fn(), location: { origin: 'http://localhost' } })
})
afterEach(() => {
  mounted.splice(0).forEach((app) => app.unmount())
  vi.unstubAllGlobals()
})

describe('ACC-02 unified satisfaction project context', () => {
  it('prevents navigation away from an unfinished confirmation form', async () => {
    const page = renderPanel(CompletionWorkbench, { projectId: 41 })
    await flush()
    expect(page.child.value.requestLeave()).toBe(true)
    page.state().formVisible = true
    expect(page.child.value.requestLeave()).toBe(false)
    expect(routeGuards.leave.at(-1)!()).toBe(false)
    expect(routeGuards.update.at(-1)!()).toBe(false)
    expect(message.warning).toHaveBeenCalledWith('请先保存或关闭完工证明')
    page.state().formVisible = false
    expect(page.child.value.requestLeave()).toBe(true)
  })
  it('forwards the same project and readonly restriction through the existing top-level page', async () => {
    const view = renderPanel(Workbench, { projectId: 51, readonly: true })
    await flush()
    expect(confirmations.getConfirmationPage).toHaveBeenCalledWith(expect.objectContaining({ projectId: 51 }))
    expect(confirmations.getConfirmationPage.mock.calls.at(-1)?.[0].survey).not.toBe(true)
    expect(view.state().workbench.$props.readonly).toBe(true)
    expect(view.state().workbench.$props.projectId).toBe(51)
    expect(view.child.value.isDirty()).toBe(false)
    expect(view.child.value.discardChanges()).toBe(true)
  })
})
