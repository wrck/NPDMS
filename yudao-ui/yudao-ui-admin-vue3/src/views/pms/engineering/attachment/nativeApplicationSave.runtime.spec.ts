import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import External from '../ext-proc/index.vue'
import Outsource from '../outsource/index.vue'
import Requisition from '../material-req/index.vue'
const api = vi.hoisted(() => ({ create: vi.fn(), get: vi.fn(), update: vi.fn(), files: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/views/pms/delivery-business/site-survey/surveyActionContext', () => ({ loadSurveyActionContext: vi.fn() }))
vi.mock('@/views/pms/delivery-business/site-survey/siteSurveyExecutionShortcut', () => ({ resolveSurveyExecution: vi.fn() }))
vi.mock('@/api/pms/engineering/site-survey/entity', () => ({ getSiteSurvey: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn() }) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getStrDictOptions: () => [], getIntDictOptions: () => [] }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 8 } }) }))
vi.mock('@/components/ProjectTag/index.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/UserTag/index.vue', () => ({ default: { render: () => null } }))
vi.mock('@/api/pms/engineering/ext-proc', () => ({
  getExternalProcurementPage: async () => ({ list: [], total: 0 }),
  createExternalProcurement: api.create, getExternalProcurement: api.get, updateExternalProcurement: api.update
}))
vi.mock('@/api/pms/engineering/outsource', () => ({
  getOutsourceRequestPage: async () => ({ list: [], total: 0 }),
  createOutsourceRequest: api.create, getOutsourceRequest: api.get, updateOutsourceRequest: api.update
}))
vi.mock('@/api/pms/engineering/material-req', () => ({
  getMaterialRequisitionPage: async () => ({ list: [], total: 0 }),
  createMaterialRequisition: api.create, getMaterialRequisition: api.get, updateMaterialRequisition: api.update
}))
vi.mock('../ext-proc/ExternalProcurementFormDialog.vue', () => ({ default: defineComponent({ setup(_, { expose }) {
  expose({ validate: async () => true, savePending: api.files }); return () => h('application-form')
} }) }))
vi.mock('../outsource/OutsourceRequestFormDialog.vue', () => ({ default: defineComponent({ setup(_, { expose }) {
  expose({ validate: async () => true, savePending: api.files }); return () => h('application-form')
} }) }))
vi.mock('../material-req/MaterialRequisitionFormDialog.vue', () => ({ default: defineComponent({ setup(_, { expose }) {
  expose({ validate: async () => true, savePending: api.files }); return () => h('application-form')
} }) }))
vi.mock('../ext-proc/ExternalProcurementDetailDialog.vue', () => ({ default: { render: () => null } }))
vi.mock('../outsource/OutsourceRequestDetailDialog.vue', () => ({ default: { render: () => null } }))
vi.mock('../material-req/MaterialRequisitionDetailDialog.vue', () => ({ default: { render: () => null } }))
beforeEach(() => {
  vi.clearAllMocks()
  api.create.mockResolvedValue(91)
  api.get.mockResolvedValue({ id: 91, projectId: 5, status: 0, version: 4 })
  api.files.mockResolvedValue(true)
})
it.each([['外采', External], ['外包', Outsource], ['领料', Requisition]] as const)('%s retains a created draft and retries failed attachments without creating twice', async (_name, component) => {
  const view = mount(component, {}, { ElTable: passthrough, ElTableColumn: tableColumn })
  try {
    const state = (view.vm as any).$.setupState
    state.openCreate()
    await nextTick()
    Object.assign(state.form, { projectId: 5 })
    api.files.mockResolvedValueOnce(false)
    await state.save()
    expect(state.form.id).toBe(91)
    expect(state.formVisible).toBe(true)
    await state.save()
    expect(api.create).toHaveBeenCalledTimes(1)
    expect(api.files).toHaveBeenLastCalledWith(91)
    expect(state.form.version).toBe(4)
    expect(state.formVisible).toBe(false)
  } finally { view.app.unmount() }
})
