import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, provide, ref } from 'vue'
import { nativeBusinessKey, type NativeBusinessContext } from '@/components/ProjectBusiness/native/nativeBusinessContext'
import Dialog from './SurveyMaterialExchangeDialog.vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ createMaterialExchange: vi.fn() }))
vi.mock('@/api/pms/engineering/material-exch', () => api)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn() }) }))
vi.mock('@/views/pms/engineering/material-exch/MaterialDevicePicker.vue', () => ({
  default: defineComponent({ setup: () => () => h('picker') })
}))
const form = defineComponent({ setup(_, { expose, slots }) {
  expose({ validate: async () => true })
  return () => h('form', slots.default?.())
} })
const render = (saveSurvey = vi.fn(async () => ({ id: '2108608319822127106', projectId: 10, name: '工勘' }))) => {
  const result = mount(Dialog, { modelValue: true, projectId: 10, saveSurvey }, { ElForm: form })
  const state = (result.vm as any).$.setupState
  state.form.serials = [{ scopeId: 60, quantity: 2 }]
  state.form.reason = '<p>接口不符</p>'
  return { ...result, state, saveSurvey }
}
beforeEach(() => { vi.clearAllMocks(); api.createMaterialExchange.mockResolvedValue(100) })
it('creates an application only after a survey receipt and carries project, source and selected quantities', async () => {
  const m = render()
  try {
    await m.state.save()
    expect(m.saveSurvey.mock.invocationCallOrder[0]).toBeLessThan(api.createMaterialExchange.mock.invocationCallOrder[0])
    expect(api.createMaterialExchange).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 10, quantity: 2, serials: [{ scopeId: 60, quantity: 2 }],
      reason: '<p>接口不符</p>', remark: '来源工勘 #2108608319822127106'
    }))
  } finally { m.app.unmount() }
})
it('does not create an application when survey save fails, retaining popup inputs', async () => {
  const m = render(vi.fn().mockRejectedValue(new Error('工勘保存失败')))
  try {
    await m.state.save()
    expect(api.createMaterialExchange).not.toHaveBeenCalled()
    expect(m.state.error).toBe('工勘保存失败')
    expect(m.state.form.serials).toHaveLength(1)
    expect(m.state.form.reason).toBe('<p>接口不符</p>')
  } finally { m.app.unmount() }
})
it('rejects a changed project and preserves inputs on application failure for retry', async () => {
  const m = render(vi.fn().mockResolvedValue({ id: '1', projectId: 11, name: '另一个工勘' }))
  try {
    await m.state.save()
    expect(api.createMaterialExchange).not.toHaveBeenCalled()
    m.saveSurvey.mockResolvedValue({ id: '1', projectId: 10, name: '工勘' })
    api.createMaterialExchange.mockRejectedValueOnce(new Error('换货保存失败'))
    await m.state.save()
    expect(m.state.error).toBe('换货保存失败')
    expect(m.state.form.serials).toHaveLength(1)
    expect(m.state.saving).toBe(false)
  } finally { m.app.unmount() }
})
it('requires materials and a nonempty reason before saving the survey', async () => {
  const m = render()
  try {
    m.state.form.serials = []; await m.state.save()
    expect(m.saveSurvey).not.toHaveBeenCalled()
    m.state.form.serials = [{ scopeId: 60, quantity: 2 }]
    m.state.form.reason = '<p><br></p>'; await m.state.save()
    expect(m.saveSurvey).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})

it('uses the hosted exchange command and keeps the popup when extensions are invalid or leaving is refused', async () => {
  const dialog = ref(), closed = vi.fn(), create = vi.fn().mockResolvedValue(100)
  const saveSurvey = vi.fn().mockResolvedValue({ id: '2108608319822127106', projectId: 10, name: '工勘' })
  const formInput = vi.fn().mockRejectedValueOnce(new Error('扩展字段未填写'))
    .mockResolvedValue({ $extensions: { handlingNote: '保留原换货数量' } })
  const requestLeave = vi.fn().mockResolvedValue(false)
  const m = mount(defineComponent({ setup() {
    provide(nativeBusinessKey, {
      api: () => ({ createMaterialExchange: create }), can: () => true, formInput, requestLeave,
      registerForm: () => () => undefined, Form: defineComponent({ setup: () => () => h('shared-form') })
    } as unknown as NativeBusinessContext)
    return () => h(Dialog, { ref: dialog, modelValue: true, projectId: 10, saveSurvey, 'onUpdate:modelValue': closed })
  } }), {}, { ElForm: form })
  try {
    const state = dialog.value.$.setupState
    state.form.serials = [{ scopeId: 60, quantity: 2 }]
    state.form.reason = '<p>接口不符</p>'
    await state.save()
    expect(saveSurvey).not.toHaveBeenCalled()
    expect(state.form.serials).toEqual([{ scopeId: 60, quantity: 2 }])
    await state.updateVisible(false)
    expect(requestLeave).toHaveBeenCalledWith('exchange')
    expect(closed).not.toHaveBeenCalled()
    await state.save()
    expect(create).toHaveBeenCalledWith(expect.objectContaining({ serials: [{ scopeId: 60, quantity: 2 }],
      remark: '来源工勘 #2108608319822127106', $extensions: { handlingNote: '保留原换货数量' } }))
    expect(api.createMaterialExchange).not.toHaveBeenCalled()
    expect(closed).toHaveBeenCalledWith(false)
  } finally { m.app.unmount() }
})
