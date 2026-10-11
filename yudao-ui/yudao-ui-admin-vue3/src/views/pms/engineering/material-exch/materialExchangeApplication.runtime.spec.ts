import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Application from './index.vue'
import { mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ __v_isRef: false, getMaterialExchangePage: vi.fn(), getMaterialExchange: vi.fn(), createMaterialExchange: vi.fn(), updateMaterialExchange: vi.fn() }))
const notices = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('../attachment/NativeAttachments.vue', () => ({ default: { render: () => null } }))
vi.mock('@/api/pms/engineering/material-exch', () => api)
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn(), getProject: vi.fn(async () => ({ projectName: '回归项目', projectCode: 'TEST-10' })) }))
vi.mock('@/api/pms/asset/device/archive', () => ({ __v_isRef: false, getDeviceArchiveRecord: vi.fn() }))
vi.mock('@/api/system/user', () => ({ __v_isRef: false, getUserPage: vi.fn() }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 9, nickname: '当前用户' } }) }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/views/pms/delivery-business/site-survey/surveyActionContext', () => ({ loadSurveyActionContext: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => notices }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getIntDictOptions: () => [], getStrDictOptions: () => [] }))
vi.mock('./MaterialDevicePicker.vue', () => ({ default: defineComponent({ setup: () => () => h('material-picker') }) }))
vi.mock('@/components/ProjectTag/index.vue', () => ({ default: defineComponent({ setup: () => () => h('project-tag') }) }))
const form = defineComponent({ setup(_, { attrs, slots, expose }) { expose({ validate: async () => true }); return () => h('form', attrs, slots.default?.()) } })
const flush = async () => { for (let i = 0; i < 6; i++) await nextTick() }
const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
const render = () => {
  const m = mount(Application, { projectId: 10 }, { ElForm: form, ElRow: passthrough, ElCol: passthrough, ElTable: passthrough, ElTableColumn: tableColumn, ElInput: passthrough, ElSelect: passthrough, ElOption: passthrough, ElDescriptions: passthrough, ElDescriptionsItem: passthrough, ElRadioGroup: passthrough, ElRadio: passthrough, Editor: passthrough, PmsEntitySelect: passthrough })
  return { ...m, state: (m.vm as any).$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  api.getMaterialExchangePage.mockResolvedValue({ list: [], total: 0 })
  api.createMaterialExchange.mockResolvedValue(100)
  api.updateMaterialExchange.mockResolvedValue(undefined)
})
it('application exposes reason and material selection with readonly metadata, preserving historical classification views', async () => {
  const m = render()
  try {
    await flush(); m.state.openCreate(); await flush()
    const formNode = nodes(m.root).find(n => n.type === 'form' && n.props?.model === m.state.form)!
    const labels = nodes(formNode).map(n => n.props?.label).filter(Boolean)
    expect(labels).toEqual(['物料选择', '项目', '单号', '申请人', '申请时间', '换货原因', '原因附件', '备注'])
    expect(m.state.form.exchangeType).toBe('INCOMPATIBLE')
    expect(m.state.form.applicantUserId).toBe(9)
  } finally { m.app.unmount() }
})
it('new application uses selected quantities/default category/current applicant and omits client time; saves remain drafts', async () => {
  const m = render()
  try {
    await flush(); m.state.openCreate(); await flush()
    m.state.updateSerials([{ scopeId: 60, quantity: 2 }, { scopeDetailId: 7, quantity: 3 }])
    m.state.form.reason = '回归原因'
    m.state.form.applicantUserId = 999; m.state.form.applyTime = '2000-01-01'; m.state.form.exchangeType = 'OTHER'
    await m.state.save()
    expect(api.createMaterialExchange).toHaveBeenCalledWith(expect.objectContaining({ name: '回归项目', quantity: 5, unit: '台', exchangeType: 'INCOMPATIBLE', applicantUserId: 9, applyTime: undefined }))
    expect(m.state.formVisible).toBe(false)
  } finally { m.app.unmount() }
})
it('missing materials prevent creating a hidden zero-quantity application', async () => {
  const m = render()
  try {
    await flush(); m.state.openCreate(); await flush(); m.state.form.reason = '原因'
    await m.state.save()
    expect(api.createMaterialExchange).not.toHaveBeenCalled()
    expect(notices.warning).toHaveBeenCalledWith('请选择物料并填写换货数量')
    expect(m.state.formVisible).toBe(true)
  } finally { m.app.unmount() }
})
it('second creation restores automatically derived name after resetting the form', async () => {
  const m = render()
  try {
    await flush(); m.state.openCreate(); await flush()
    m.state.formVisible = false; m.state.openCreate(); await flush()
    expect(m.state.form.name).toBe('回归项目')
    expect(m.state.form.code).toContain('ME-TEST-10-')
  } finally { m.app.unmount() }
})
it('editing retains original classification/applicant/time and hidden historical content', async () => {
  api.getMaterialExchange.mockResolvedValue({ id: 100, projectId: 10, code: 'OLD', name: '旧申请', exchangeType: 'DAMAGED', applicantUserId: 6, applyTime: '2026-09-01', reason: '旧原因', reasonFiles: '旧附件', remark: '旧备注', quantity: 2, version: 3, serials: [{ scopeId: 60, quantity: 2 }] })
  const m = render()
  try {
    await flush(); await m.state.openEdit({ id: 100 }); await flush(); m.state.form.reason = '补充原因'
    expect(m.state.projectLabel).toBe('回归项目（TEST-10）')
    await m.state.save()
    expect(api.updateMaterialExchange).toHaveBeenCalledWith(expect.objectContaining({ exchangeType: 'DAMAGED', applicantUserId: 6, applyTime: '2026-09-01', reasonFiles: '旧附件', remark: '旧备注', version: 3 }))
    expect(api.createMaterialExchange).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})
it('failed save preserves selected materials and reason for retry', async () => {
  api.createMaterialExchange.mockRejectedValueOnce(new Error('server rejected'))
  const m = render()
  try {
    await flush(); m.state.openCreate(); await flush(); m.state.updateSerials([{ scopeId: 60, quantity: 2 }]); m.state.form.reason = '保留原因'
    await expect(m.state.save()).rejects.toThrow('server rejected')
    expect(m.state.formVisible).toBe(true)
    expect(m.state.form.serials).toEqual([{ scopeId: 60, quantity: 2 }])
    expect(m.state.form.reason).toBe('保留原因')
    expect(m.state.saving).toBe(false)
  } finally { m.app.unmount() }
})
