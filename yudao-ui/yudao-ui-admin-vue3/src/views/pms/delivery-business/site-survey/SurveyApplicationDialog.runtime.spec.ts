import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Dialog from './SurveyApplicationDialog.vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ material: vi.fn(), procurement: vi.fn(), outsource: vi.fn() }))
vi.mock('@/api/pms/engineering/material-req', () => ({ createMaterialRequisition: api.material }))
vi.mock('@/api/pms/engineering/ext-proc', () => ({ createExternalProcurement: api.procurement }))
vi.mock('@/api/pms/engineering/outsource', () => ({ createOutsourceRequest: api.outsource }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 8 } }) }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn() }) }))
vi.mock('@/views/pms/engineering/material-req/MaterialRequisitionFormDialog.vue', () => ({ default: defineComponent({ setup(_, { expose }) { expose({ validate: async () => true }); return () => h('form') } }) }))
vi.mock('@/views/pms/engineering/ext-proc/ExternalProcurementFormDialog.vue', () => ({ default: defineComponent({ setup(_, { expose }) { expose({ validate: async () => true }); return () => h('form') } }) }))
vi.mock('@/views/pms/engineering/outsource/OutsourceRequestFormDialog.vue', () => ({ default: defineComponent({ setup(_, { expose }) { expose({ validate: async () => true }); return () => h('form') } }) }))
beforeEach(() => vi.resetAllMocks())
const render = (kind: 'material' | 'procurement' | 'outsource') => {
  const getSurvey = () => ({ projectId: 7, name: 'survey' })
  const saveSurvey = vi.fn().mockResolvedValue({ id: '2108611480670629890', projectId: 7, name: 'survey' })
  const getExecution = vi.fn().mockResolvedValue({ task: { taskId: '12', executionId: '13' } })
  const closed = vi.fn(), created = vi.fn()
  const m = mount(Dialog, { kind, modelValue: true, getSurvey, saveSurvey, getExecution,
    'onUpdate:modelValue': closed, onCreated: created })
  return { ...m, state: (m.vm as any).$.setupState, saveSurvey, getExecution, closed, created }
}
it.each(['material', 'procurement', 'outsource'] as const)('saves survey before %s and preserves inputs through failure', async kind => {
  const m = render(kind)
  try {
    await nextTick()
    m.state.form.code = 'APPLICATION-1'
    m.saveSurvey.mockRejectedValueOnce(new Error('survey failed'))
    await m.state.save()
    expect(api[kind]).not.toHaveBeenCalled()
    expect(m.closed).not.toHaveBeenCalled()
    expect(m.state.form.code).toBe('APPLICATION-1')
    api[kind].mockRejectedValueOnce(new Error('application failed'))
    await m.state.save()
    expect(m.closed).not.toHaveBeenCalled()
    expect(m.state.form.code).toBe('APPLICATION-1')
    api[kind].mockResolvedValue(22)
    await m.state.save()
    expect(api[kind]).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 7,
      triggerSource: 'SITE_SURVEY', triggerRefId: '2108611480670629890', code: 'APPLICATION-1' }))
    expect(m.saveSurvey.mock.invocationCallOrder[1]).toBeLessThan(api[kind].mock.invocationCallOrder[0])
    expect(m.created).toHaveBeenCalledWith(kind)
    expect(m.closed).toHaveBeenCalledWith(false)
    if (kind === 'outsource') expect(api.outsource).toHaveBeenLastCalledWith(expect.objectContaining({ siteSurveyExecution: { task: { taskId: '12', executionId: '13' } } }))
  } finally { m.app.unmount() }
})
it('does not create when project changes or outsource execution cannot be resolved', async () => {
  const m = render('outsource')
  try {
    await nextTick()
    m.saveSurvey.mockResolvedValueOnce({ id: 12, projectId: 9 })
    await m.state.save()
    expect(api.outsource).not.toHaveBeenCalled()
    m.getExecution.mockRejectedValueOnce(new Error('ambiguous node'))
    await m.state.save()
    expect(api.outsource).not.toHaveBeenCalled()
    expect(m.state.error).toBe('ambiguous node')
    expect(m.closed).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})
