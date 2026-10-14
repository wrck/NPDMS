import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import {
  mount,
  passthrough,
  tableColumn
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Workbench from './index.vue'

const api = vi.hoisted(() => ({
  getConfirmationPage: vi.fn(),
  getConfirmation: vi.fn(),
  saveConfirmation: vi.fn(),
  issueConfirmation: vi.fn(),
  deleteConfirmation: vi.fn(),
  rejectCompletionCertificate: vi.fn(),
  archiveCompletionCertificate: vi.fn()
}))
const projectApi = vi.hoisted(() => ({ getProject: vi.fn(), getProjectPage: vi.fn() }))
const deviceApi = vi.hoisted(() => ({ getDevicePage: vi.fn() }))
const commerceApi = vi.hoisted(() => ({ getDeliveryScopePage: vi.fn(), getSalesOrderLinePage: vi.fn() }))
const profileApi = vi.hoisted(() => ({ getUserProfile: vi.fn() }))
const fileApi = vi.hoisted(() => ({ updateFile: vi.fn() }))
const message = vi.hoisted(() => ({
  confirm: vi.fn(),
  delConfirm: vi.fn(),
  success: vi.fn(),
  warning: vi.fn()
}))
const editor = vi.hoisted(() => ({ validate: vi.fn() }))
const print = vi.hoisted(() => ({ open: vi.fn(), close: vi.fn(), exporting: false }))
const guards = vi.hoisted(() => ({ leave: [] as Array<() => boolean> }))
vi.mock('@/api/pms/acceptance/completion-certificate', () => api)
vi.mock('@/api/pms/project/projects', () => projectApi)
vi.mock('@/api/pms/asset/device', () => deviceApi)
vi.mock('@/api/pms/commerce', () => commerceApi)
vi.mock('@/api/system/user/profile', () => profileApi)
vi.mock('@/api/system/user', () => ({ getUserPage: vi.fn(async () => ({ list: [] })) }))
vi.mock('@/api/pms/platform/dynamic-form', () => ({
  getTemplateSelection: vi.fn(async () => ({ list: [], total: 0 }))
}))
vi.mock('@/api/infra/file', () => fileApi)
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ user: { id: 1, nickname: '当前用户' }, getUser: { id: 1, nickname: '当前用户' } })
}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/utils/dict', () => ({
  DICT_TYPE: { PMS_COMPLETION_CERT_STATUS: 'completion_status' },
  getIntDictOptions: () => []
}))
vi.mock('@/components/FormCreate/src/customerConfirmation', () => ({
  signaturePngFile: () => new File(['png'], '签字.png')
}))
vi.mock('./CompletionCertificateForm.vue', () => ({
  default: {
    setup: (_: any, { expose }: any) => {
      expose({ validate: editor.validate })
      return () => null
    }
  }
}))
vi.mock('../satisfaction/CompletionCertificateSheet.vue', () => ({
  default: { render: () => null }
}))
vi.mock('../satisfaction/TemplatePanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../satisfaction/TaskPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/BusinessEntity/DeliveryPanel.vue', () => ({
  default: { render: () => null }
}))
vi.mock('./ConfirmationLinkDialog.vue', () => ({
  default: {
    setup: (_: any, { expose }: any) => {
      expose({ open: vi.fn(), close: vi.fn() })
      return () => null
    }
  }
}))
vi.mock('./ConfirmationPrintTemplates.vue', () => ({ default: { render: () => null } }))
vi.mock('./ConfirmationPrintPreview.vue', () => ({
  default: {
    setup: (_: any, { expose }: any) => {
      expose({ open: print.open, close: print.close, isExporting: () => print.exporting })
      return () => null
    }
  }
}))
vi.mock('./confirmationPrintForm', () => ({ completionDisplay: (value: any) => value }))
vi.mock('vue-router', () => ({
  onBeforeRouteLeave: (guard: () => boolean) => guards.leave.push(guard),
  onBeforeRouteUpdate: vi.fn()
}))

const mounted: { unmount: () => void }[] = []
const flush = async () => {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
const deferred = <T>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
const draft = (projectId = 41) => ({
  id: 410,
  projectId,
  name: `项目${projectId}`,
  customerUnit: '客户单位',
  serviceType: '工程实施',
  completionDate: '2026-09-30',
  status: 0,
  version: 2
})
const renderPage = (initial: { projectId?: number; readonly?: boolean } = { projectId: 41 }) => {
  const props = reactive(initial),
    child = ref<any>()
  const wrapper = defineComponent({ setup: () => () => h(Workbench, { ...props, ref: child }) })
  const stubs = Object.fromEntries(
    [
      'ElInput',
      'ElSelect',
      'ElOption',
      'ElFormItem',
      'ElSkeleton',
      'ElResult',
      'ElAlert',
      'ElDrawer',
      'ElDialog',
      'ElTable',
      'PmsEntitySelect',
      'DictTag'
    ].map((name) => [name, passthrough])
  )
  const { app } = mount(wrapper, {}, { ...stubs, ElTableColumn: tableColumn })
  mounted.push(app)
  return { props, state: () => child.value.$.setupState, public: () => child.value }
}
beforeEach(() => {
  vi.clearAllMocks()
  print.exporting = false
  guards.leave.length = 0
  api.getConfirmationPage.mockResolvedValue({ list: [], total: 0 })
  api.getConfirmation.mockResolvedValue({ certificate: draft(), history: [] })
  api.saveConfirmation.mockResolvedValue(410)
  projectApi.getProject.mockImplementation(async (id: number) => ({
    id,
    projectName: `项目${id}`,
    customerName: '客户单位',
    contractNo: '合同01'
  }))
  deviceApi.getDevicePage.mockReset().mockResolvedValue({ list: [], total: 0 })
  commerceApi.getDeliveryScopePage.mockReset().mockResolvedValue({ list: [], total: 0 })
  commerceApi.getSalesOrderLinePage.mockReset().mockResolvedValue({ list: [], total: 0 })
  profileApi.getUserProfile.mockReset().mockResolvedValue({ id: 1, nickname: '当前用户', mobile: '13800000000' })
  message.confirm.mockResolvedValue(undefined)
  message.delConfirm.mockResolvedValue(undefined)
  editor.validate.mockResolvedValue(true)
  fileApi.updateFile.mockResolvedValue({ data: '/saved-signature.png' })
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
})
afterEach(() => {
  mounted.splice(0).forEach((app) => app.unmount())
  vi.unstubAllGlobals()
})

describe('completion workbench save and context protection', () => {
  it('issues the saved completion once while the linked survey is being prepared', async () => {
    const issued = deferred<any>()
    api.issueConfirmation.mockReturnValueOnce(issued.promise)
    const page = renderPage()
    await flush()
    const command = { row: draft(), action: 'issueConfirmation', actionText: '发起调查' }
    const pending = page.state().handleAction(command).catch((error: Error) => error)
    await flush()
    expect(api.issueConfirmation).toHaveBeenCalledExactlyOnceWith(410)
    await page.state().handleAction(command)
    expect(api.issueConfirmation).toHaveBeenCalledOnce()
    issued.resolve({ id: 410, taskId: 510, signPath: '/fixture-only', tokenExpiresAt: '2026-10-20' })
    expect(await pending).toBeUndefined()
    expect(page.state().pendingActions.size).toBe(0)
  })
  it('does not issue a linked survey when the project becomes readonly during confirmation', async () => {
    const confirm = deferred<void>()
    message.confirm.mockReturnValueOnce(confirm.promise)
    const page = renderPage()
    await flush()
    const pending = page.state().handleAction({ row: draft(), action: 'issueConfirmation', actionText: '发起调查' })
    page.props.readonly = true
    await flush()
    confirm.resolve()
    await pending
    expect(api.issueConfirmation).not.toHaveBeenCalled()
  })
  it('prefills all seven original equipment products and device counts when only two order lines exist', async () => {
    const products = [8, 4, 4, 16, 4, 2, 2].map((quantity, index) => ({
      deviceType: `原清单产品${index + 1}`, deviceModel: `MODEL-${index + 1}`, quantity
    }))
    const devices = products.flatMap((product, index) => Array.from({ length: product.quantity }, (_, serial) => ({
      sn: `SN-${index}-${serial}`, productCode: ` CODE-${index + 1} `,
      productName: ` ${product.deviceType} `, productModel: ` ${product.deviceModel} `
    })))
    deviceApi.getDevicePage.mockResolvedValueOnce({ total: 40, list: devices })
    commerceApi.getDeliveryScopePage.mockResolvedValueOnce({ total: 2, list: [
      { id: 1, orderLineId: 11, orderNo: 'SO-A', lineNo: '1', productCode: 'CODE-5', allocatedQuantity: 4, details: [] },
      { id: 2, orderLineId: 12, orderNo: 'SO-A', lineNo: '2', productCode: 'CODE-4', allocatedQuantity: 16, details: [] }
    ] })
    const page = renderPage()
    await flush()
    await page.state().openForm()
    expect(page.state().deviceRows).toEqual(products)
    await page.state().save()
    expect(api.saveConfirmation).toHaveBeenCalledWith(expect.objectContaining({
      engineerName: '当前用户', engineerUserId: 1, engineerContact: '13800000000',
      devices: products
    }))
  })
  it('uses the authenticated profile for new forms and retains login identity when profile loading fails', async () => {
    const page = renderPage()
    await flush()
    await page.state().openForm()
    expect(page.state().form.engineerName).toBe('当前用户')
    expect(page.state().form.engineerUserId).toBe(1)
    expect(page.state().form.engineerContact).toBe('13800000000')
    page.state().closeForm()
    profileApi.getUserProfile.mockRejectedValueOnce(new Error('个人资料暂不可用'))
    await page.state().openForm()
    expect(page.state().form.engineerName).toBe('当前用户')
    expect(page.state().form.engineerUserId).toBe(1)
    expect(page.state().form.engineerContact).toBe('')
    expect(page.state().formError).toBe(false)
  })
  it('loads later device pages and groups the full equipment list without replacing names or models', async () => {
    deviceApi.getDevicePage.mockResolvedValueOnce({ total: 102,
      list: Array.from({ length: 100 }, (_, index) => ({ sn: `SN-${index}`, productCode: ' A ', productModel: ' MODEL-A ', productName: '原清单设备A' }))
    }).mockResolvedValueOnce({ total: 102, list: [
      { sn: 'SN-100', productCode: 'A', productModel: 'MODEL-A', productName: '原清单设备A' },
      { sn: 'SN-B', productCode: 'B', productModel: 'MODEL-B', productName: '原清单设备B' }
    ] })
    const page = renderPage()
    await flush()
    await page.state().openForm()
    expect(page.state().deviceRows).toEqual([
      { deviceType: '原清单设备A', deviceModel: 'MODEL-A', quantity: 101 },
      { deviceType: '原清单设备B', deviceModel: 'MODEL-B', quantity: 1 }
    ])
    expect(deviceApi.getDevicePage).toHaveBeenLastCalledWith({ projectId: 41, pageNo: 2, pageSize: 100 })
  })
  it('retains original equipment products and device quantities when order scopes are unavailable', async () => {
    deviceApi.getDevicePage.mockResolvedValueOnce({ total: 1, list: [{ sn: 'SN-1', productCode: 'A', productName: '原清单设备', productModel: 'MODEL-A' }] })
    commerceApi.getDeliveryScopePage.mockRejectedValueOnce(new Error('订单范围不可读'))
    const page = renderPage()
    await flush()
    await page.state().openForm()
    expect(page.state().deviceRows).toEqual([{ deviceType: '原清单设备', deviceModel: 'MODEL-A', quantity: 1 }])
    expect(page.state().formError).toBe(false)
  })
  it('keeps an empty equipment source empty without inventing a default quantity', async () => {
    const page = renderPage()
    await flush()
    await page.state().openForm()
    expect(page.state().deviceRows).toEqual([])
    await page.state().save()
    expect(api.saveConfirmation).toHaveBeenCalledWith(expect.objectContaining({ devices: [] }))
  })
  it('does not replace saved engineer details or manually cleared device rows when editing', async () => {
    api.getConfirmation.mockResolvedValueOnce({ certificate: {
      ...draft(), contractNo: '保存合同', engineerUserId: 81, engineerName: '保存工程师',
      engineerContact: '手填联系方式', devices: []
    }, history: [] })
    const page = renderPage()
    await flush()
    await page.state().openForm(draft())
    expect(page.state().form.engineerName).toBe('保存工程师')
    expect(page.state().form.engineerContact).toBe('手填联系方式')
    expect(page.state().deviceRows).toEqual([])
    expect(profileApi.getUserProfile).not.toHaveBeenCalled()
    expect(deviceApi.getDevicePage).not.toHaveBeenCalled()
    expect(commerceApi.getDeliveryScopePage).not.toHaveBeenCalled()
  })
  it('blocks saving instead of using partial device counts when a later device page fails', async () => {
    deviceApi.getDevicePage
      .mockResolvedValueOnce({ list: Array.from({ length: 100 }, (_, index) => ({ sn: `SN-${index}`, productCode: 'A', productName: '防火墙' })), total: 101 })
      .mockRejectedValueOnce(new Error('后续设备清单页读取失败'))
    const page = renderPage()
    await flush()
    await page.state().openForm()
    expect(page.state().formError).toBe(true)
    expect(page.state().deviceRows).toEqual([])
    await page.state().save()
    expect(api.saveConfirmation).not.toHaveBeenCalled()
  })
  it('drops late equipment and profile results after closing a new form and opening an existing draft', async () => {
    const pendingDevices = deferred<any>(), pendingProfile = deferred<any>()
    deviceApi.getDevicePage.mockReturnValueOnce(pendingDevices.promise)
    profileApi.getUserProfile.mockReturnValueOnce(pendingProfile.promise)
    const saved = { ...draft(), contractNo: '保存合同', engineerName: '保存工程师', engineerContact: '保存联系方式',
      devices: [{ deviceType: '保存设备', deviceModel: '保存型号', quantity: 7 }] }
    api.getConfirmation.mockResolvedValueOnce({ certificate: saved, history: [] })
    const page = renderPage()
    await flush()
    const creating = page.state().openForm()
    await flush()
    page.state().closeForm()
    await page.state().openForm(draft())
    pendingDevices.resolve({ list: [{ sn: 'SN-OLD', productCode: '旧项目设备', productName: '旧项目设备' }], total: 101 })
    pendingProfile.resolve({ id: 1, nickname: '晚到工程师', mobile: '晚到联系方式' })
    await creating
    expect(page.state().form.engineerName).toBe('保存工程师')
    expect(page.state().form.engineerContact).toBe('保存联系方式')
    expect(page.state().deviceRows).toEqual(saved.devices)
    expect(deviceApi.getDevicePage).toHaveBeenCalledTimes(1)
  })
  it('keeps the form and input on save failure and closes only after a successful retry', async () => {
    const page = renderPage()
    await flush()
    await page.state().openForm(draft())
    await flush()
    page.state().form.customerUnit = '修改后的客户单位'
    api.saveConfirmation.mockRejectedValueOnce(new Error('版本已变化'))
    await page.state().save()
    expect(page.state().formVisible).toBe(true)
    expect(page.state().form.customerUnit).toBe('修改后的客户单位')
    expect(page.state().saveError).toBe('版本已变化')
    expect(page.state().saving).toBe(false)
    expect(message.success).not.toHaveBeenCalled()
    await page.state().save()
    expect(api.saveConfirmation).toHaveBeenLastCalledWith(
      expect.objectContaining({
        id: 410,
        version: 2,
        customerUnit: '修改后的客户单位',
        completionDate: '2026-09-30'
      })
    )
    expect(page.state().formVisible).toBe(false)
    expect(message.success).toHaveBeenCalledWith('保存成功')
  })
  it('does not send a save command after form validation fails', async () => {
    const page = renderPage()
    await flush()
    await page.state().openForm(draft())
    await flush()
    editor.validate.mockRejectedValueOnce(new Error('请选择工程服务类型'))
    await page.state().save()
    expect(api.saveConfirmation).not.toHaveBeenCalled()
    expect(page.state().formVisible).toBe(true)
  })
  it('sends one save and blocks close, discard and navigation while saving', async () => {
    const pending = deferred<number>()
    api.saveConfirmation.mockReturnValueOnce(pending.promise)
    const page = renderPage()
    await flush()
    await page.state().openForm(draft())
    await flush()
    const first = page.state().save()
    await flush()
    await page.state().save()
    page.state().closeForm()
    expect(api.saveConfirmation).toHaveBeenCalledTimes(1)
    expect(page.state().formVisible).toBe(true)
    expect(page.public().discardChanges()).toBe(false)
    expect(guards.leave.at(-1)!()).toBe(false)
    pending.resolve(410)
    await first
    expect(page.public().isDirty()).toBe(false)
  })
  it('does not save when the project becomes readonly during a signature upload', async () => {
    const pending = deferred<{ data: string }>()
    fileApi.updateFile.mockReturnValueOnce(pending.promise)
    const page = renderPage()
    await flush()
    await page.state().openForm(draft())
    await flush()
    page.state().customerSignDataUrl = 'data:image/png;base64,cG5n'
    const saving = page.state().save()
    await flush()
    page.props.readonly = true
    await flush()
    pending.resolve({ data: '/uploaded-signature.png' })
    await saving
    expect(api.saveConfirmation).not.toHaveBeenCalled()
    expect(page.state().formVisible).toBe(false)
    expect(message.success).not.toHaveBeenCalled()
  })
  it('discards late project defaults from a closed form when a new project form is opened', async () => {
    const pending = deferred<any>()
    projectApi.getProject.mockReturnValueOnce(pending.promise)
    const page = renderPage()
    await flush()
    const old = page.state().openForm()
    page.props.projectId = 42
    await flush()
    await page.state().openForm()
    pending.resolve({ projectName: '旧项目', customerName: '旧客户', contractNo: '旧合同' })
    await old
    expect(page.state().form.projectId).toBe(42)
    expect(page.state().projectNameCache).toBe('项目42')
    expect(page.state().form.customerUnit).toBe('客户单位')
    expect(page.state().formLoading).toBe(false)
  })
  it('drops a late list and detail after switching projects', async () => {
    const pendingList = deferred<any>(),
      pendingDetail = deferred<any>()
    api.getConfirmationPage.mockReturnValueOnce(pendingList.promise)
    api.getConfirmation.mockReturnValueOnce(pendingDetail.promise)
    const page = renderPage()
    const opened = page.state().openDetail(draft())
    page.props.projectId = 42
    await flush()
    pendingList.resolve({ list: [draft()], total: 1 })
    pendingDetail.resolve({ certificate: draft(), history: [] })
    await opened
    await flush()
    expect(page.state().rows).toEqual([])
    expect(page.state().detailVisible).toBe(false)
    expect(page.state().detail).toBeUndefined()
    expect(api.getConfirmationPage).toHaveBeenLastCalledWith(
      expect.objectContaining({ projectId: 42 })
    )
  })
  it('prevents readonly creation and redirects a submitted record to readonly details', async () => {
    const readonly = renderPage({ projectId: 41, readonly: true })
    await flush()
    await readonly.state().openForm()
    expect(readonly.state().formVisible).toBe(false)
    const page = renderPage()
    await flush()
    api.getConfirmation.mockResolvedValueOnce({
      certificate: { ...draft(), status: 2 },
      history: []
    })
    await page.state().openForm({ ...draft(), status: 2 })
    expect(page.state().detailVisible).toBe(true)
    expect(page.state().formVisible).toBe(false)
    await page.state().save()
    expect(api.saveConfirmation).not.toHaveBeenCalled()
  })
  it('prevents leaving or discarding during controlled PDF registration', async () => {
    const page = renderPage()
    await flush()
    print.exporting = true
    expect(page.public().isDirty()).toBe(true)
    expect(page.public().discardChanges()).toBe(false)
    expect(page.public().requestLeave()).toBe(false)
    expect(message.warning).toHaveBeenCalledWith('请等待 PDF 登记完成')
  })
  it('shows the current project name without replacing the saved proof content used by printing', async () => {
    const saved = {
      ...draft(),
      name: '原保存证明名称',
      status: 2,
      completionPrintSnapshot: '{"engine":"FORM_CREATE_ELEMENT_PLUS","formRulesJson":[]}'
    }
    api.getConfirmation.mockResolvedValueOnce({ certificate: saved, history: [] })
    const page = renderPage()
    await flush()
    await page.state().openDetail(saved)
    expect(page.state().detailProjectName).toBe('项目41')
    expect(page.state().detail.certificate.name).toBe('原保存证明名称')
    await page.state().printDetail(page.state().detail)
    expect(print.open).toHaveBeenCalledWith(
      expect.objectContaining({
        certificate: expect.objectContaining({ name: '原保存证明名称', version: 2 })
      }),
      { engine: 'FORM_CREATE_ELEMENT_PLUS', formRulesJson: [] }
    )
  })
})
