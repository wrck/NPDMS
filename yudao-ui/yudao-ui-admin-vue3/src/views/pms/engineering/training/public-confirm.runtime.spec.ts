import { afterEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import TrainingConfirmation from './public-confirm.vue'

const api = vi.hoisted(() => ({ inspect: vi.fn(), confirm: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { token: 'fixture' }, query: { tenantId: '1' } }) }))
vi.mock('@/api/pms/engineering/training', () => ({ inspectPublicTraining: api.inspect, confirmPublicTraining: api.confirm }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '2026-10-24' }))
vi.mock('@/components/FormCreate/src/customerConfirmation', () => ({ customerFormOption: { form: {} } }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), warning: vi.fn() }) }))
const apps: Array<{ unmount: () => void }> = []
afterEach(() => { apps.splice(0).forEach(app => app.unmount()); vi.clearAllMocks() })

it.each([true, false])('renders confirmed training as an immutable record, signature present: %s', async signed => {
  api.inspect.mockResolvedValue({ status: 2, name: '已确认培训', confirmationFormRules: JSON.stringify([
    { type: 'input', field: 'signOpinion' }, { type: 'signature', field: 'signatureImageDataUrl' }
  ]), signatureImageDataUrl: signed ? 'data:image/png;base64,fixture' : undefined })
  const view = mount(TrainingConfirmation, {}, { ElSkeleton: passthrough, ElAlert: passthrough, FormCreate: passthrough })
  apps.push(view.app)
  for (let i = 0; i < 4; i++) { await Promise.resolve(); await nextTick() }
  const state = (view.vm as any).$.setupState
  expect(textOf(view.root)).toContain('感谢您的确认')
  expect(textOf(view.root)).not.toContain('签字并确认培训记录')
  expect(textOf(view.root)).not.toContain('链接有效至')
  expect(state.formOption.form.disabled).toBe(true)
  expect(state.formRules.every((rule: any) => rule.props.disabled)).toBe(true)
  expect(state.formRules.some((rule: any) => rule.field === 'signatureImageDataUrl')).toBe(signed)
  expect(api.confirm).not.toHaveBeenCalled()
})
